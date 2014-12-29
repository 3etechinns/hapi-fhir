package ca.uhn.fhir.jpa.provider;

import java.util.List;
import java.util.Map;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.model.dev.resource.Conformance;
import ca.uhn.fhir.model.dev.resource.Conformance.Rest;
import ca.uhn.fhir.model.dev.resource.Conformance.RestResource;
import ca.uhn.fhir.model.dev.resource.Conformance.RestResourceSearchParam;
import ca.uhn.fhir.model.dev.valueset.ResourceTypeEnum;
import ca.uhn.fhir.model.dev.valueset.SearchParamTypeEnum;
import ca.uhn.fhir.model.primitive.BoundCodeDt;
import ca.uhn.fhir.model.primitive.DecimalDt;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.provider.dev.ServerConformanceProvider;
import ca.uhn.fhir.util.ExtensionConstants;

import javax.servlet.http.HttpServletRequest;

public class JpaConformanceProviderDev extends ServerConformanceProvider {

	private String myImplementationDescription;
	private IFhirSystemDao mySystemDao;
	private volatile Conformance myCachedValue;
	private RestfulServer myRestfulServer;

	public JpaConformanceProviderDev(RestfulServer theRestfulServer, IFhirSystemDao theSystemDao) {
		super(theRestfulServer);
		myRestfulServer = theRestfulServer;
		mySystemDao = theSystemDao;
		super.setCache(false);
	}

	@Override
	public Conformance getServerConformance(HttpServletRequest theRequest) {
		Conformance retVal = myCachedValue;

		Map<String, Long> counts = mySystemDao.getResourceCounts();

		FhirContext ctx = myRestfulServer.getFhirContext();
		
		retVal = super.getServerConformance(theRequest);
		for (Rest nextRest : retVal.getRest()) {
			for (RestResource nextResource : nextRest.getResource()) {

				// Add resource counts
				Long count = counts.get(nextResource.getTypeElement().getValueAsString());
				if (count != null) {
					nextResource.addUndeclaredExtension(false, ExtensionConstants.CONF_RESOURCE_COUNT, new DecimalDt(count));
				}
				
				// Add chained params
				for (RestResourceSearchParam nextParam : nextResource.getSearchParam()) {
					if (nextParam.getTypeElement().getValueAsEnum() == SearchParamTypeEnum.REFERENCE) {
						List<BoundCodeDt<ResourceTypeEnum>> targets = nextParam.getTarget();
						for (BoundCodeDt<ResourceTypeEnum> next : targets) {
							RuntimeResourceDefinition def = ctx.getResourceDefinition(next.getValue());
							for (RuntimeSearchParam nextChainedParam : def.getSearchParams()) {
								nextParam.addChain(nextChainedParam.getName());
							}
						}
					}
				}
				
			}
		}

		retVal.getImplementation().setDescription(myImplementationDescription);
		myCachedValue = retVal;
		return retVal;
	}

	public void setImplementationDescription(String theImplDesc) {
		myImplementationDescription = theImplDesc;
	}

}
