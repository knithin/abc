package abc.aspectj.ast;


import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import polyglot.util.CollectionUtil;
import polyglot.util.TypedList;
import polyglot.util.InternalCompilerError;

import polyglot.ast.Block;
import polyglot.ast.TypeNode;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ast.ClassDecl;
import polyglot.ast.MethodDecl;
import polyglot.ast.Expr;
import polyglot.ast.ClassBody;

import polyglot.types.Flags;
import polyglot.util.Position;
import polyglot.util.CodeWriter;

import polyglot.visit.NodeVisitor;
import polyglot.visit.PrettyPrinter;
import polyglot.visit.TypeBuilder;

import polyglot.types.Context;
import polyglot.types.TypeSystem;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.visit.TypeChecker;

import polyglot.ext.jl.ast.ClassDecl_c;

import abc.aspectj.types.AspectJFlags;
import abc.aspectj.types.AspectJTypeSystem;

import abc.aspectj.visit.ContainsAspectInfo;

import abc.weaving.aspectinfo.*;

/**
 * @author Oege de Moor
 * A <code>AspectDecl</code> is the definition of an aspect, abstract aspect,
 * or privileged. It may be a public or other top-level aspect, or an inner
 * named aspect.
 */
public class AspectDecl_c extends ClassDecl_c implements AspectDecl, ContainsAspectInfo
{
    
    protected PerClause per;

    private boolean per_object;

    public AspectDecl_c(Position pos, boolean is_privileged, Flags flags, String name,
                        TypeNode superClass, List interfaces, PerClause per, AspectBody body) {
	     super(pos,
	           AspectJFlags.aspectclass(is_privileged ? AspectJFlags.privilegedaspect(flags): flags),
	           name,superClass,interfaces,body);
         this.per = per;
	 this.per_object = per instanceof PerThis || per instanceof PerTarget;
    }
    
    /**
     * construct a dummy aspectOf method that always returns null
	* it potentially throws org.aspectj.lang.NoAspectBoundException, so that is loaded,
	* if it is a per-object associated aspect, it takes one parameter of type Object, otherwise none
	*/
    private MethodDecl aspectOf(NodeFactory nf,AspectJTypeSystem ts) {
	TypeNode tn = nf.AmbTypeNode(position(),name());
	Expr nl = nf.NullLit(position());
	Block bl = nf.Block(position()).append(nf.Return(position(),nl));
				
	TypeNode nab = nf.CanonicalTypeNode(position(),ts.NoAspectBound());
	List args = new LinkedList();
	if (per_object) {
	    TypeNode obj = nf.CanonicalTypeNode(position(),ts.Object());
	    polyglot.ast.Formal f = nf.Formal(position(),Flags.NONE,obj,"thisparam");
	    args.add(f);
	}
	List thrws = new LinkedList(); thrws.add(nab);
	MethodDecl md = nf.MethodDecl(position(),Flags.PUBLIC.Static(),tn,"aspectOf",args,thrws,bl); 
	return md; 
    }
    
    /**
     * construct a dummy hasAspect method that always returns true 
     */
    private MethodDecl hasAspect(NodeFactory nf, AspectJTypeSystem ts) {
    	TypeNode bool = nf.CanonicalTypeNode(position(),ts.Boolean());
    	Expr b = nf.BooleanLit(position(),true);
    	Block bl = nf.Block(position()).append(nf.Return(position(),b));
    	
    	List args = new LinkedList();
	if (per_object) {
	    TypeNode obj = nf.CanonicalTypeNode(position(),ts.Object());
	    polyglot.ast.Formal f = nf.Formal(position(),Flags.NONE,obj,"thisparam");
	    args.add(f);
	}
    	List thrws = new LinkedList();
    	MethodDecl md = nf.MethodDecl(position(),Flags.PUBLIC.Static(),bool,"hasAspect",args,thrws,bl);
    	return md;
    }
    
    /** 
     * add the aspectOf and hasAspect methods to the aspect class, but only if it is concrete
    */
    
	public NodeVisitor buildTypesEnter(TypeBuilder tb) throws SemanticException {
		if (!flags().isAbstract()) {
		    NodeFactory nf = tb.nodeFactory();
		    AspectJTypeSystem ts = (AspectJTypeSystem) tb.typeSystem();
		    MethodDecl aspectOf = aspectOf(nf,ts);
		    MethodDecl hasAspect = hasAspect(nf,ts);
		    body = body().addMember(aspectOf).addMember(hasAspect); 
		    // against the polyglot doctrine of functional rewrites... 
		}
		return super.buildTypesEnter(tb);     
	} 
		
	protected AspectDecl_c reconstruct(TypeNode superClass, List interfaces, PerClause per, ClassBody body) {
		   if (superClass != this.superClass || ! CollectionUtil.equals(interfaces, this.interfaces) || 
		        per != this.per || body != this.body) {
			   AspectDecl_c n = (AspectDecl_c) copy();
			   n.superClass = superClass;
			   n.interfaces = TypedList.copyAndCheck(interfaces, TypeNode.class, true);
			   n.per = per;
			   n.body = body;
			   return n;
		   }

		   return this;
	   }


	public Node visitChildren(NodeVisitor v) {
		TypeNode superClass = (TypeNode) visitChild(this.superClass, v);
		List interfaces = visitList(this.interfaces, v);
		PerClause per = (PerClause) visitChild(this.per,v);
		ClassBody body = (ClassBody) visitChild(this.body, v);
		return reconstruct(superClass, interfaces, per, body);
	}
	
	public Context enterScope(Node child, Context c) {
			if (child == this.per ) {
				TypeSystem ts = c.typeSystem();
				c = c.pushClass(type, ts.staticTarget(type).toClass());
			}
			return super.enterScope(child, c);
		}
	
	public void prettyPrintHeader(CodeWriter w, PrettyPrinter tr) {
		
		    // need to overwrite, because ClassDecl_c only knows of interfaces and classes
			w.write(AspectJFlags.clearAspectclass(flags).translate());
	        w.write("aspect ");

			w.write(name);

			if (superClass() != null) {
				w.write(" extends ");
				print(superClass(), w, tr);
			}

			if (! interfaces.isEmpty()) {
				if (flags.isInterface()) {
					w.write(" extends ");
				}
				else {
					w.write(" implements ");
				}

				for (Iterator i = interfaces().iterator(); i.hasNext(); ) {
					TypeNode tn = (TypeNode) i.next();
					print(tn, w, tr);

					if (i.hasNext()) {
						w.write (", ");
					}
				}
			}

			w.write(" {");
		}
		
	public Node typeCheck(TypeChecker tc) throws SemanticException {
		if (type().isInnerClass())
			throw new InternalCompilerError("Inner aspects have not been implemented yet.");
		return super.typeCheck(tc);
	}
	
    public void update(GlobalAspectInfo gai, Aspect current_aspect) {
	Per p = (per == null ? new Singleton(position()) : per.makeAIPer());
	AbcClass cl = gai.getClass(type());
	Aspect a = new Aspect(cl, p, position());
	gai.addAspect(a);
		    
	String this_type = this.type().toString();
	String aspectOf = this_type+" "+this_type+".aspectOf("+(per_object?"java.lang.Object":"")+")";
	String hasAspect = "boolean "+this_type+".hasAspect("+(per_object?"java.lang.Object":"")+")";
	MethodCategory.register(aspectOf, MethodCategory.ASPECT_INSTANCE);
	MethodCategory.register(hasAspect, MethodCategory.ASPECT_INSTANCE);
    }
}
