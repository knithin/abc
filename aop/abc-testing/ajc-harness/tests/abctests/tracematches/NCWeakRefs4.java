public class NCWeakRefs4 {
    public static void foo(Object o) { }
    public static void bar() { 
	//System.out.println("Matched " + matched + " times."); 
    }

    public static long matched = 0;

    static class BigObject { int[] big_array = new int[1024*1024]; }

    public static void main(String[] args) {
	for(int j = 0; j < 10; j++) {
	    for(int i = 0; i < 10; i++) {
		Object o = new BigObject();
		foo(o);
	    }
	    bar();
	    bar();
	    System.out.print(".");
	    System.gc(); System.gc(); System.gc(); System.gc(); System.gc();
	}
	System.out.println();
	if(matched != 100) throw new RuntimeException("Matched " + matched + " times, rather than 100.");
    }
}

aspect A {
    tracematch(Object o) {
	sym foo before : call(* *.foo(Object)) && args(o);
	sym bar before : call(* *.bar());

	foo bar bar {
	    NCWeakRefs4.matched++; System.out.print("+");
	}
    }
}
