package ca.uhn.fhir.rest.server;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu.resource.Patient;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.util.PortUtil;
import ca.uhn.fhir.util.UrlUtil;

public class TokenParameterTest {

	private static CloseableHttpClient ourClient;
	private static FhirContext ourCtx = FhirContext.forDstu1();
	private static int ourPort;

	private static Server ourServer;
	private static TokenOrListParam ourLastOrList;

	@Before
	public void before() {
		ourLastOrList = null;
	}

	/**
	 * Test #192
	 */
	@Test
	public void testOrListWithEscapedValue() throws Exception {
		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?identifier=" + UrlUtil.escape("system|code-include-but-not-end-with-comma\\,suffix"));
		HttpResponse status = ourClient.execute(httpGet);
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertEquals(200, status.getStatusLine().getStatusCode());

		assertEquals(1, ourLastOrList.getListAsCodings().size());
		assertEquals("system", ourLastOrList.getListAsCodings().get(0).getSystemElement().getValue());
		assertEquals("code-include-but-not-end-with-comma,suffix", ourLastOrList.getListAsCodings().get(0).getCodeElement().getValue());
	}

	@AfterClass
	public static void afterClass() throws Exception {
		ourServer.stop();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ourPort = PortUtil.findFreePort();
		ourServer = new Server(ourPort);

		DummyPatientResourceProvider patientProvider = new DummyPatientResourceProvider();

		ServletHandler proxyHandler = new ServletHandler();
		RestfulServer servlet = new RestfulServer(ourCtx);
		servlet.setFhirContext(ourCtx);
		servlet.setResourceProviders(patientProvider);
		ServletHolder servletHolder = new ServletHolder(servlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class DummyPatientResourceProvider implements IResourceProvider {

		@Search
		public List<Patient> findPatientByString(@RequiredParam(name = Patient.SP_IDENTIFIER) final TokenOrListParam theIdentifiers) {
			ArrayList<Patient> retVal = new ArrayList<Patient>();
			ourLastOrList = theIdentifiers;
			return retVal;
		}

		@Search
		public List<Patient> findPatientByStringParam(@RequiredParam(name = "str") StringParam theParam) {
			ArrayList<Patient> retVal = new ArrayList<Patient>();

			if (theParam.isExact() && theParam.getValue().equals("aaa")) {
				Patient patient = new Patient();
				patient.setId("1");
				retVal.add(patient);
			}
			if (!theParam.isExact() && theParam.getValue().toLowerCase().equals("aaa")) {
				Patient patient = new Patient();
				patient.setId("2");
				retVal.add(patient);
			}

			return retVal;
		}

		@Search
		public List<Patient> findPatientWithOptional(@OptionalParam(name = "ccc") StringParam theParam) {
			ArrayList<Patient> retVal = new ArrayList<Patient>();

			if (theParam.isExact() && theParam.getValue().equals("aaa")) {
				Patient patient = new Patient();
				patient.setId("1");
				retVal.add(patient);
			}
			if (!theParam.isExact() && theParam.getValue().toLowerCase().equals("aaa")) {
				Patient patient = new Patient();
				patient.setId("2");
				retVal.add(patient);
			}

			return retVal;
		}

		@Override
		public Class<? extends IResource> getResourceType() {
			return Patient.class;
		}

	}

}