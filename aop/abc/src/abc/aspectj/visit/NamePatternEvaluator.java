
package abc.aspectj.visit;

import abc.aspectj.ast.*;

import polyglot.ast.*;
import polyglot.visit.*;
import polyglot.util.*;

import java.util.*;

public class NamePatternEvaluator extends NodeVisitor {
    protected Set/*<String>*/ classes;
    protected Set/*<String>*/ packages;
    protected PCNode context;

    protected abc.aspectj.ExtensionInfo ext_info;

    public NamePatternEvaluator(abc.aspectj.ExtensionInfo ext_info) {
	this.ext_info = ext_info;
    }

    public NodeVisitor enter(Node n) {
	if (n instanceof ClassDecl) {
	    String name = ((ClassDecl)n).type().fullName();
	    context = ext_info.hierarchy.getClass(name);
	    return this;
	}
	return this;
    }

    public Node override(Node n) {
	if (n instanceof SourceFile) {
	    classes = new HashSet();
	    packages = new HashSet();
	    Iterator ii = ((SourceFile)n).imports().iterator();
	    while (ii.hasNext()) {
		Import i = (Import)ii.next();
		if (i.kind() == Import.CLASS) {
		    classes.add(i.name());
		}
		if (i.kind() == Import.PACKAGE) {
		    packages.add(i.name());
		}
	    }
	    return null;
	}
	if (n instanceof NamePattern) {
	    //Position p = n.position();
	    //System.out.println("Evaluating name pattern on "+p.file()+":"+p.line());
	    ext_info.pattern_matcher.computeMatches((NamePattern)n, context, classes, packages);
	}
	return null;
    }

}

