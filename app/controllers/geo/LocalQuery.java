package controllers.geo;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;

import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.index.query.BoolQueryBuilder;

import com.fasterxml.jackson.databind.JsonNode;

public class LocalQuery {

	public static SearchResponse queryLocal(final String aTerm) {
		final BoolQueryBuilder queryBuilder = boolQuery();
		queryBuilder.must(matchQuery(Constants.TERM, aTerm));

		SearchRequestBuilder searchBuilder = GeoElasticsearch.ES_CLIENT.prepareSearch(GeoElasticsearch.ES_INDEX)
				.setTypes(GeoElasticsearch.ES_TYPE);
		return searchBuilder.setQuery(queryBuilder).setSize(1).execute().actionGet();
	}

	public static SearchResponse queryLocal(final String aStreet, final String aCity, final String aCountry) {
		final BoolQueryBuilder queryBuilder = boolQuery();
		queryBuilder.must(matchQuery(Constants.STREET, aStreet)).must(matchQuery(Constants.CITY, aCity));

		SearchRequestBuilder searchBuilder = GeoElasticsearch.ES_CLIENT.prepareSearch(GeoElasticsearch.ES_INDEX)
				.setTypes(GeoElasticsearch.ES_TYPE);
		return searchBuilder.setQuery(queryBuilder).setSize(1).execute().actionGet();
	}

	public static void addLocal(final JsonNode aGeoNode) {
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

}