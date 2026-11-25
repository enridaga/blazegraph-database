package com.bigdata.rdf.sail.webapp.client;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.client.transport.HttpConversation;
import org.eclipse.jetty.client.transport.HttpRequest;
import org.eclipse.jetty.client.transport.HttpResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.openrdf.query.resultio.TupleQueryResultFormat;
import org.openrdf.rio.RDFFormat;

public class MockRemoteRepository extends RemoteRepository {

	public final static class Data {
		public ConnectOptions opts;
		public HttpRequest request;
		public IPreparedTupleQuery query;
		protected Response.CompleteListener listener;
	}
	public Data data;

	private MockRemoteRepository(RemoteRepositoryManager mgr, String sparqlEndpointURL, IRemoteTx tx, Data data) {
		super(mgr, sparqlEndpointURL, tx);
		this.data = data;
	}

	/**
	 * A request which, instead of going to the network, answers with a canned
	 * TSV (tuple queries) or N-Triples (graph queries) response.
	 */
	private static final class MockRequest extends HttpRequest {

		private final Data data;
		private final String tupleQueryResponse;
		private final String graphQueryResponse;

		MockRequest(HttpClient client, URI uri, Data data, String tupleQueryResponse, String graphQueryResponse) {
			super(client, new HttpConversation(), uri);
			this.data = data;
			this.tupleQueryResponse = tupleQueryResponse;
			this.graphQueryResponse = graphQueryResponse;
		}

		@Override
		public void send(Response.CompleteListener listener) {
			// Store HTTP request
			data.request = this;
			data.listener = listener;
			if (listener instanceof JettyResponseListener) {
				final JettyResponseListener l = (JettyResponseListener) listener;
				final HttpResponse response = new HttpResponse(this) {
					@Override
					public int getStatus() {
						return 200;
					}
				};
				final String requestMimeType = getHeaders().get(HttpHeader.ACCEPT).split(";")[0];
				final TupleQueryResultFormat tupleQueryMimeType = TupleQueryResultFormat.forMIMEType(requestMimeType);
				final String responseMimeType;
				final String responseContent;
				if (tupleQueryMimeType != null) {
					responseMimeType = TupleQueryResultFormat.TSV.getDefaultMIMEType();
					responseContent = tupleQueryResponse;
				} else {
					responseMimeType = RDFFormat.NTRIPLES.getDefaultMIMEType();
					responseContent = graphQueryResponse;
				}
				response.headers(h -> h.add(HttpHeader.CONTENT_TYPE, responseMimeType));
				l.onHeaders(response);
				final ByteBuffer buf = ByteBuffer.wrap(responseContent.getBytes(StandardCharsets.UTF_8));
				l.onContent(response, org.eclipse.jetty.io.Content.Chunk.from(buf, true), () -> { });
				l.onSuccess(response);
				l.onComplete(new Result(this, response));
			}
		}
	}

	public static MockRemoteRepository create(final String tupleQueryResponse, final String graphQueryResponse) {
		// pojo to retrieve values from mock service
		final Data data = new Data();

		String serviceURL = "http://localhost";
		final HttpClient httpClient = new HttpClient() {
			@Override
			public boolean isStopped() {
				return false;
			}
		};
		Executor executor = Executors.newCachedThreadPool();
		RemoteRepositoryManager mgr = new RemoteRepositoryManager(serviceURL,
				httpClient, executor) {
			@Override
			public JettyResponseListener doConnect(ConnectOptions opts) throws Exception {
				// Store connection options
				data.opts = opts;
				return super.doConnect(opts);
			}

			@Override
			public Request newRequest(final String uri, final String method) {
				return new MockRequest(httpClient, URI.create(uri), data, tupleQueryResponse, graphQueryResponse)
						.method(getMethod(method));
			}
		};

		return new MockRemoteRepository(mgr, serviceURL, null, data);
	}

	@Override
	public IPreparedTupleQuery prepareTupleQuery(String query)
			throws Exception {
		// Store IPreparedTupleQuery
		return data.query = super.prepareTupleQuery(query);
	}
	
}
