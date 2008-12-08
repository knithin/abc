package jastaddmodules.translator;

import jastaddmodules.translator.oomodules.AbstractModule;
import jastaddmodules.translator.oomodules.ConcreteModule;
import jastaddmodules.translator.oomodules.ModuleInterface;
import jastaddmodules.translator.oomodules.WeakModuleInterface;
import jastaddmodules.translator.osgi.BundleBucket;
import jastaddmodules.translator.osgi.StaticBundleEnvironment;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.ImportPackageSpecification;
import org.eclipse.osgi.service.resolver.VersionRange;

public class BundleEnvironmentTranslator {
	//Passes: 
	//	generateOOModules -> creates an AbstractModule instance for each BundleInstance (includes exported packages)
	//	generateOverrides -> generates the overrides relations from the versions.
	//						implicit assumption that a higher version overrides a lower version
	//	generateRBInterfaces -> creates ModuleInterfaces derived from the required 
	//							bundle constraints, and adds these as imports and 
	//							implements in the appropriate modules
	//	generateIPInterfaces -> creates WeakModuleInterfaces derived from the import
	//							package declarations
	//	generateSystemModule -> generates the top level system module, which does the
	//							wiring using replace declarations to fill in
	//							the interface references with an actual module. Should
	//							take into consideration the singleton attribute of
	//							bundles
	
	StaticBundleEnvironment bundleEnv;
	HashMap<BundleDescription, AbstractModule> bundleMap = new HashMap<BundleDescription, AbstractModule>();
	HashMap<String, ModuleInterface> rbInterfaceMap = new HashMap<String, ModuleInterface>();
	HashMap<String, WeakModuleInterface> ipInterfaceMap = new HashMap<String, WeakModuleInterface>();
	ConcreteModule systemModule; //filled in by the last pass, contains the replace declarations that links the interfaces to the 
	
	public BundleEnvironmentTranslator(StaticBundleEnvironment bundleEnv) {
		this.bundleEnv = bundleEnv;
	}
	
	public void translate() {
		generateOOModules();
		generateOverrides();
		generateRBInterfaces();
		generateIPInterfaces();
		generateSystemModule();
		
		//DEBUG
		for (AbstractModule module : bundleMap.values()) {
			System.out.println("//----------------------------------------");
			System.out.print(module.toString());
		}
		for (AbstractModule moduleInterface : rbInterfaceMap.values()) {
			System.out.println("//----------------------------------------");
			System.out.print(moduleInterface.toString());
		}
		for (AbstractModule weakModuleInterface : ipInterfaceMap.values()) {
			System.out.println("//----------------------------------------");
			System.out.print(weakModuleInterface.toString());
		}
	}
	
	//PASS---------------------------------------------------------
	protected void generateOOModules() {
		for (BundleDescription bundle : bundleEnv.getAllBundles()) {
			assert (bundleMap.get(bundle) == null) : "Entry already present for " + bundle;
			AbstractModule module = createOOModuleFromBundle(bundle);
			//exported packages
			for (ExportPackageDescription epd : bundle.getExportPackages()) {
				module.addExportedPackage(epd.getName());
			}
			
			bundleMap.put(bundle, module);
		}
	}
	
	protected AbstractModule createOOModuleFromBundle(BundleDescription bundle) {
		return new ConcreteModule(makeBundleOOName(bundle));
	}
	
	private String makeBundleOOName(BundleDescription bundle) {
		return bundle.getName() + "_" + bundle.getVersion().toString();
	}
	
	//PASS---------------------------------------------------------
	protected void generateOverrides() {
		for (BundleBucket bucket : bundleEnv.getAllBundleBuckets()) {
			List<AbstractModule> prevModules = new LinkedList<AbstractModule>();
			for (Iterator<BundleDescription> iter = bucket.getAllBundles().iterator();
					iter.hasNext(); ) {
				BundleDescription bundle = iter.next();
				AbstractModule module = bundleMap.get(bundle);
				assert (module != null) : "Not found in bundle map: " + bundle;
				
				module.addOverridenModules(prevModules);
				prevModules.add(module);
			}
		}
	}
	
	//PASS---------------------------------------------------------
	protected void generateRBInterfaces() {
		for (BundleDescription bundle : bundleEnv.getAllBundles()) {
			AbstractModule module = bundleMap.get(bundle);
			assert (module != null) : "No matching module for bundle " + bundle;
			for (BundleSpecification reqSpec : bundle.getRequiredBundles()) {
				ModuleInterface rbInterface = 
					makeNewRBInterface(reqSpec.getName(), reqSpec.getVersionRange());
				module.addImportedModule(rbInterface, rbInterface.getName());
			}
		}
	}
	
	protected ModuleInterface getRBInterface(String interfaceName) {
		ModuleInterface ret = rbInterfaceMap.get(interfaceName);
		return ret;
	}
	
	protected ModuleInterface makeNewRBInterface(String symbolicName, VersionRange range) {
		String interfaceName = makeRBInterfaceName(symbolicName, range);
		//if already created, return the existing interface
		if (getRBInterface(interfaceName) != null) {
			return getRBInterface(interfaceName);
		}
		ModuleInterface ret = new ModuleInterface(interfaceName);
		rbInterfaceMap.put(interfaceName, ret);
		
		//create the exported packages from the intersection of the packages that are
		//exported by the visible bundles that are available. Also, add the interface
		//to the implements list of the matching modules
		boolean first = true;
		for (BundleDescription bundle: bundleEnv.getBundles(symbolicName, range)) {
			//export package conjunction
			if (first) {
				ret.addExportedPackages(getExportedPackages(bundle));
			} else {
				ret.getExportedPackages().retainAll(getExportedPackages(bundle));
			}
			//interface implements
			AbstractModule module = bundleMap.get(bundle);
			assert (module != null) : "Module not found for bundle " + bundle;
			module.addImplementedInterface(ret);
			first = false;
		}
		return ret;
	}
	
	protected String makeRBInterfaceName(String symbolicName, VersionRange range) {
		String ret = "";
		
		//return is I_internal_symbolicName_v_minver_maxver. The _ before minver and maxver is doubled
		//if they are exclusive i.e., com.xyz (1.6,1.7] is encoded as com.xyz_v__1.6_1.7
		ret += "I_internal_" + symbolicName + "_v";
		if (range.getIncludeMinimum()) {
			ret += "_";
		} else {
			ret += "__";
		}
		ret += range.getMinimum().toString();
		if (range.getIncludeMaximum()) {
			ret += "_";
		} else {
			ret += "__";
		}
		ret += range.getMaximum().toString();
		
		return ret;
	}
	
	//PASS---------------------------------------------------------
	protected void generateIPInterfaces() {
		//TODO: Implement
		int counter = 0;
		for (BundleDescription bundle : bundleEnv.getAllBundles()) {
			AbstractModule module = bundleMap.get(bundle);
			assert (module != null) : "Module not found for bundle " + bundle;
			if (bundle.getImportPackages().length > 0) {
				ModuleInterface newInterface = makeIPInterface(bundle, counter);
				module.addImportedModule(newInterface, newInterface.getName());
				counter ++;
			}
		}
	}
	
	//create a weak module interface, which exports the packages that the context imports
	protected WeakModuleInterface makeIPInterface(BundleDescription context, int counter) {
		String interfaceName = makeIPInterfaceName(context, counter);
		assert (ipInterfaceMap.get(interfaceName) == null) : "Weak interface of the same name already exists: " + interfaceName; 
		WeakModuleInterface ret = new WeakModuleInterface(interfaceName);
		ret.addExportedPackages(getImportedPackages(context));
		ipInterfaceMap.put(ret.getName(), ret);
		return ret;
	}
	
	protected String makeIPInterfaceName(BundleDescription context, int counter) { 
		String ret = "";
		
		ret += "WI_internal_" + context.getSymbolicName() + "_ctr_" + counter;
		
		return ret;
	}
	
	//PASS---------------------------------------------------------
	protected void generateSystemModule() {
		//TODO: Implement
	}
	
	
	//Util methods
	private Collection<String> getExportedPackages(BundleDescription bundle) {
		Collection<String> ret = new LinkedList<String>();
		for (ExportPackageDescription packageDesc : bundle.getExportPackages()) {
			ret.add(packageDesc.getName());
		}
		return ret;
	}
	
	private Collection<String> getImportedPackages(BundleDescription bundle) {
		Collection<String> ret = new LinkedList<String>();
		for (ImportPackageSpecification packageDesc : bundle.getImportPackages()) {
			ret.add(packageDesc.getName());
		}
		return ret;
	}
}