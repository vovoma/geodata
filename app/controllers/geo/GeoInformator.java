package controllers.geo;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;

import java.io.IOException;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.mvc.Controller;
import play.mvc.Result;

public class GeoInformator extends Controller {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String TERM = "term";
	private static final String STREET = "street";
	private static final String CITY = "city";
	private static final String COUNTRY = "country";
	private static final String GEOCODE = "geocode";
	private static final String POSTALCODE = "postalcode";
	private static final String NOT_FOUND = "Not found (404): ";
	private static final String SEPARATOR = ",";

	// private static final Client mClient = GeoElasticsearch.ES_CLIENT;

	// for production
	public GeoInformator() {
	}

	public static Result getLatAndLong(String query) throws JSONException, IOException {
		JsonNode latLong = getLatLong(query);
		if (latLong == null) {
			return notFound(NOT_FOUND.concat(query));
		}
		return ok(latLong.get("latitude").asText().concat(SEPARATOR).concat(latLong.get("longitude").asText()));
	}

	public static Result getPostCodeExplicitNr(String street, String number, String city, String country)
			throws JSONException, IOException {
		return getPostCode(street + " " + number, city, country);
	}

	public static Result getLatExplicitNr(String street, String number, String city, String country)
			throws JSONException, IOException {
		return getLat(street + " " + number, city, country);
	}

	public static Result getLongExplicitNr(String street, String number, String city, String country)
			throws JSONException, IOException {
		return getLong(street + " " + number, city, country);
	}

	public static Result getPostCode(String street, String city, String country) throws JSONException, IOException {
		JsonNode postCode = getPostalCode(street, city, country);
		if (postCode == null) {
			return notFound(NOT_FOUND.concat(street).concat("+").concat(city).concat("+").concat(country));
		}
		return ok(postCode.asText());
	}

	public static Result getLat(final String street, final String city, final String country)
			throws JSONException, IOException {
		JsonNode latLong = getLatLong(street, city, country);
		if (latLong == null) {
			return notFound(NOT_FOUND.concat(street).concat("+").concat(city).concat("+").concat(country));
		}
		return ok(latLong.get("latitude").asText());
	}

	public static Result getLong(final String street, final String city, final String country)
			throws JSONException, IOException {
		JsonNode latLong = getLatLong(street, city, country);
		if (latLong == null) {
			return notFound(NOT_FOUND.concat(street).concat("+").concat(city).concat("+").concat(country));
		}
		return ok(latLong.get("longitude").asText());
	}

	private static JsonNode getPostalCode(final String aStreet, final String aCity, final String aCountry)
			throws JSONException, IOException {
		JsonNode geoNode = getFirstGeoNode(aStreet, aCity, aCountry);
		if (geoNode == null) {
			return null;
		}
		return geoNode.get(POSTALCODE);
	}

	public static JsonNode getLatLong(final String aQuery) throws JSONException, IOException {
		JsonNode geoNode = getFirstGeoNode(aQuery);
		if (geoNode == null) {
			return null;
		}
		return geoNode.get(GEOCODE);
	}

	public static JsonNode getLatLong(final String aStreet, final String aCity, final String aCountry)
			throws JSONException, IOException {
		JsonNode geoNode = getFirstGeoNode(aStreet, aCity, aCountry);
		if (geoNode == null) {
			return null;
		}
		return geoNode.get(GEOCODE);
	}

	private static JsonNode getFirstGeoNode(final String aStreet, final String aCity, final String aCountry)
			throws JSONException, IOException {
		SearchResponse response = queryLocal(aStreet, aCity, aCountry);
		JsonNode geoNode;
		if (response == null || response.getHits().getTotalHits() == 0) {
			// this address information has never been queried before
			geoNode = createGeoNode(aStreet, aCity, aCountry);
			addLocal(geoNode);
		} else {
			geoNode = MAPPER.valueToTree(response.getHits().hits()[0].getSource());
		}
		return geoNode;
	}

	private static JsonNode getFirstGeoNode(final String aQuery) throws JSONException, IOException {
		SearchResponse response = queryLocal(aQuery);
		JsonNode geoNode;
		if (response == null || response.getHits().getTotalHits() == 0) {
			// this address information has never been queried before
			geoNode = createGeoNode(aQuery);
			addLocal(geoNode);
		} else {
			geoNode = MAPPER.valueToTree(response.getHits().hits()[0].getSource());
		}
		return geoNode;
	}

	private static SearchResponse queryLocal(final String aTerm) {
		final BoolQueryBuilder queryBuilder = boolQuery();
		queryBuilder.must(matchQuery(TERM, aTerm));

		SearchRequestBuilder searchBuilder = GeoElasticsearch.ES_CLIENT.prepareSearch(GeoElasticsearch.ES_INDEX)
				.setTypes(GeoElasticsearch.ES_TYPE);
		return searchBuilder.setQuery(queryBuilder).setSize(1).execute().actionGet();
	}

	private static SearchResponse queryLocal(final String aStreet, final String aCity, final String aCountry) {
		final BoolQueryBuilder queryBuilder = boolQuery();
		queryBuilder.must(matchQuery(STREET, aStreet)).must(matchQuery(CITY, aCity));

		SearchRequestBuilder searchBuilder = GeoElasticsearch.ES_CLIENT.prepareSearch(GeoElasticsearch.ES_INDEX)
				.setTypes(GeoElasticsearch.ES_TYPE);
		return searchBuilder.setQuery(queryBuilder).setSize(1).execute().actionGet();
	}

	private static void addLocal(final JsonNode aGeoNode) {
		int retries = 40;
		while (retries > 0) {
			try {
				GeoElasticsearch.ES_CLIENT.prepareIndex(GeoElasticsearch.ES_INDEX, GeoElasticsearch.ES_TYPE)
						.setSource(aGeoNode.toString()).execute().actionGet();
				GeoElasticsearch.ES_CLIENT.admin().indices().refresh(new RefreshRequest()).actionGet();
				break; // stop retry-while
			} catch (NoNodeAvailableException e) {
				retries--;
				try {
					Thread.sleep(10000);
				} catch (InterruptedException x) {
					x.printStackTrace();
				}
				System.err.printf("Retry indexing record %s: %s (%s more retries)\n", e.getMessage(), retries);
			}
		}
	}

	private static ObjectNode createGeoNode(final String aQuery) throws JSONException, IOException {
		// grid data of this geo node:
		ObjectNode geoNode = buildGeoNode(aQuery);
		// data enrichment to this geo node:
		JSONObject wikidata = WikidataQuery.getFirstHit(aQuery);
		if (wikidata != null) {
			double latitude = WikidataQuery.getLat(wikidata);
			double longitude = WikidataQuery.getLong(wikidata);
			geoNode.put(GEOCODE, new ObjectMapper().readTree( //
					String.format("{\"latitude\":\"%s\",\"longitude\":\"%s\"}", latitude, longitude)));
		}
		return geoNode;
	}

	private static ObjectNode createGeoNode(final String aStreet, final String aCity, final String aCountry)
			throws JSONException, IOException {
		// grid data of this geo node:
		ObjectNode geoNode = buildGeoNode(aStreet, aCity, aCountry);
		// data enrichment to this geo node:
		JSONObject nominatim = NominatimQuery.getFirstHit(aStreet, aCity, aCountry);
		if (nominatim != null) {
			double latitude = NominatimQuery.getLat(nominatim);
			double longitude = NominatimQuery.getLong(nominatim);
			String postalcode = (String) NominatimQuery.getPostcode(nominatim);
			geoNode.put(GEOCODE, new ObjectMapper().readTree( //
					String.format("{\"latitude\":\"%s\",\"longitude\":\"%s\"}", latitude, longitude)));
			geoNode.put(POSTALCODE, postalcode);
		}
		return geoNode;
	}

	private static ObjectNode buildGeoNode(final String aStreet, final String aCity, final String aCountry) {
		ObjectNode geoObject;
		geoObject = MAPPER.createObjectNode();
		geoObject.put(STREET, aStreet);
		geoObject.put(CITY, aCity);
		geoObject.put(COUNTRY, aCountry);
		return geoObject;
	}

	private static ObjectNode buildGeoNode(final String aQuery) {
		ObjectNode geoObject;
		geoObject = MAPPER.createObjectNode();
		geoObject.put(TERM, aQuery);
		return geoObject;
	}

}
