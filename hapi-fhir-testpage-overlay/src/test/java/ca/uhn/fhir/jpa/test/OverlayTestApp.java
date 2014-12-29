package ca.uhn.fhir.jpa.test;

import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.JpaConformanceProviderDstu1;
import ca.uhn.fhir.jpa.provider.JpaSystemProvider;
import ca.uhn.fhir.jpa.rp.dev.DiagnosticReportResourceProvider;
import ca.uhn.fhir.jpa.rp.dev.ObservationResourceProvider;
import ca.uhn.fhir.jpa.rp.dev.OrganizationResourceProvider;
import ca.uhn.fhir.jpa.rp.dev.PatientResourceProvider;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.TagList;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.dstu.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu.resource.DiagnosticReport;
import ca.uhn.fhir.model.dstu.resource.Observation;
import ca.uhn.fhir.model.dstu.resource.Organization;
import ca.uhn.fhir.model.dstu.resource.Patient;
import ca.uhn.fhir.model.dstu.resource.Questionnaire;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.IGenericClient;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.server.FifoMemoryPagingProvider;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;

public class OverlayTestApp {

	private static ClassPathXmlApplicationContext ourAppCtx;

	@SuppressWarnings({ "unchecked" })
	public static void main(String[] args) throws Exception {
		{
			int myPort = 8888;
			Server server = new Server(myPort);

			WebAppContext root = new WebAppContext();

			root.setContextPath("/");
			root.setDescriptor("src/main/webapp/WEB-INF/web.xml");
			root.setResourceBase("src/main/webapp");

			root.setParentLoaderPriority(true);

			server.setHandler(root);

			server.start();

		}

		ourAppCtx = new ClassPathXmlApplicationContext("hapi-fhir-server-resourceproviders-dev.xml", "hapi-fhir-server-resourceproviders-dstu1.xml", "fhir-spring-test-config.xml");
		ServletContextHandler proxyHandler = new ServletContextHandler();
		proxyHandler.setContextPath("/");

		/*
		 * DEV resources
		 */

		RestfulServer restServerDev = new RestfulServer();
		restServerDev.setPagingProvider(new FifoMemoryPagingProvider(10));
		restServerDev.setImplementationDescription("This is a great server!!!!");
		restServerDev.setFhirContext(ourAppCtx.getBean("myFhirContextDev", FhirContext.class));
		List<IResourceProvider> rpsDev = (List<IResourceProvider>) ourAppCtx.getBean("myResourceProvidersDev", List.class);
		restServerDev.setResourceProviders(rpsDev);

		JpaSystemProvider systemProvDev = (JpaSystemProvider) ourAppCtx.getBean("mySystemProviderDev", JpaSystemProvider.class);
		restServerDev.setPlainProviders(systemProvDev);

		ServletHolder servletHolder = new ServletHolder();
		servletHolder.setServlet(restServerDev);
		proxyHandler.addServlet(servletHolder, "/fhir/contextDev/*");

		/*
		 * DSTU resources
		 */

		RestfulServer restServerDstu1 = new RestfulServer();
		restServerDstu1.setPagingProvider(new FifoMemoryPagingProvider(10));
		restServerDstu1.setImplementationDescription("This is a great server!!!!");
		restServerDstu1.setFhirContext(ourAppCtx.getBean("myFhirContextDstu1", FhirContext.class));
		List<IResourceProvider> rpsDstu1 = (List<IResourceProvider>) ourAppCtx.getBean("myResourceProvidersDstu1", List.class);
		restServerDstu1.setResourceProviders(rpsDstu1);

		JpaSystemProvider systemProvDstu1 = (JpaSystemProvider) ourAppCtx.getBean("mySystemProviderDstu1", JpaSystemProvider.class);
		restServerDstu1.setPlainProviders(systemProvDstu1);

		servletHolder = new ServletHolder();
		servletHolder.setServlet(restServerDstu1);
		proxyHandler.addServlet(servletHolder, "/fhir/contextDstu1/*");

		int port = 8887;
		Server server = new Server(port);

		// base = "http://fhir.healthintersections.com.au/open";
		// base = "http://spark.furore.com/fhir";

		server.setHandler(proxyHandler);
		server.start();

		if (true) {
			String base = "http://localhost:" + port + "/fhir/contextDstu1";
			IGenericClient client = restServerDstu1.getFhirContext().newRestfulGenericClient(base);
			client.setLogRequestAndResponse(true);

			Organization o1 = new Organization();
			o1.getName().setValue("Some Org");
			MethodOutcome create = client.create(o1);
			IdDt orgId = create.getId();

			Patient p1 = new Patient();
			p1.addIdentifier("foo:bar", "12345");
			p1.addName().addFamily("Smith").addGiven("John");
			p1.getManagingOrganization().setReference(orgId);

			TagList list = new TagList();
			list.addTag("http://hl7.org/fhir/tag", "urn:happytag", "This is a happy resource");
			ResourceMetadataKeyEnum.TAG_LIST.put(p1, list);
			client.create(p1);

			List<IResource> resources = restServerDstu1.getFhirContext().newJsonParser().parseBundle(IOUtils.toString(OverlayTestApp.class.getResourceAsStream("/test-server-seed-bundle.json"))).toListOfResources();
			client.transaction(resources);

			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);
			client.create(p1);

			client.setLogRequestAndResponse(true);
			client.create(p1);

		}

	}

	public static class ProviderWithRequiredAndOptional implements IResourceProvider {

		@Description(shortDefinition = "This is a query by date!")
		@Search
		public List<DiagnosticReport> findDiagnosticReportsByPatient(@RequiredParam(name = DiagnosticReport.SP_SUBJECT + '.' + Patient.SP_IDENTIFIER) IdentifierDt thePatientId, @OptionalParam(name = DiagnosticReport.SP_NAME) TokenOrListParam theNames,
				@OptionalParam(name = DiagnosticReport.SP_DATE) DateRangeParam theDateRange, @IncludeParam(allow = { "DiagnosticReport.result" }) Set<Include> theIncludes) throws Exception {
			return null;
		}

		@Description(shortDefinition = "This is a query by issued.. blah blah foo bar blah blah")
		@Search
		public List<DiagnosticReport> findDiagnosticReportsByPatientIssued(@RequiredParam(name = DiagnosticReport.SP_SUBJECT + '.' + Patient.SP_IDENTIFIER) IdentifierDt thePatientId, @OptionalParam(name = DiagnosticReport.SP_NAME) TokenOrListParam theNames,
				@OptionalParam(name = DiagnosticReport.SP_ISSUED) DateRangeParam theDateRange, @IncludeParam(allow = { "DiagnosticReport.result" }) Set<Include> theIncludes) throws Exception {
			return null;
		}

		@Override
		public Class<? extends IResource> getResourceType() {
			return DiagnosticReport.class;
		}

	}

}
