package abc.main;

/** A class for storing debug flags. Default values go here;
    if you make a MyDebug class you can override them in the constructor
    there.

    @author Ganesh Sittampalam
*/
public class Debug {
    public static Debug v;
    static {
	try {
	    v=(Debug) 
		(ClassLoader.getSystemClassLoader()
		.loadClass("abc.main.MyDebug")
		 .newInstance());
	} catch(ClassNotFoundException e) {
	    v=new Debug();
	} catch(Exception e) {
	    System.err.println("Unknown failure trying to instantiate custom debug instance");
	    v=new Debug();
	}
    }
    public static Debug v() {
	return v;
    }


    public boolean matcherTest=false;

    // Weaver
    public boolean weaverDriver=false;  // main driver for weaver
    public boolean aspectCodeGen=false; // inserting stuff into aspect class
    public boolean genStaticJoinPoints=false; // collect and gen SJP
    public boolean shadowPointsSetter=false; // collect shadow points
    public boolean pointcutCodeGen=false; // main pointcut generator
    public boolean beforeWeaver=false;
    public boolean afterReturningWeaver=false;
    public boolean afterThrowingWeaver=false;
    public boolean aroundWeaver=false;
}
