/* abc - The AspectBench Compiler
 * Copyright (C) 2004 Ganesh Sittampalam
 *
 * This compiler is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This compiler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this compiler, in the file LESSER-GPL;
 * if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package abc.soot.util;

import java.util.*;
import soot.*;
import soot.jimple.*;
import soot.util.*;
import soot.toolkits.scalar.*;
import soot.toolkits.graph.*;

/** An analysis to check whether or not local variables have been
 *  initialised.
 *  @author Ganesh Sittampalam
 */
public class InitAnalysis extends ForwardFlowAnalysis {
    FlowSet allLocals;

    public InitAnalysis(UnitGraph g) {
	super(g);
	Chain locs=g.getBody().getLocals();
	allLocals=new ArraySparseSet();
	Iterator it=locs.iterator();
	while(it.hasNext()) {
	    Local loc=(Local) it.next();
	    allLocals.add(loc);
	}

	doAnalysis();
    }

    protected Object entryInitialFlow() {
	return new ArraySparseSet();
    }
    protected Object newInitialFlow() {
	FlowSet ret=new ArraySparseSet();
	allLocals.copy(ret);
	return ret;
    }

    protected void flowThrough(Object in,Object unit,Object out) {
	FlowSet inSet=(FlowSet) in;
	FlowSet outSet=(FlowSet) out;
	Stmt s=(Stmt) unit;

	inSet.copy(outSet);

	if(s instanceof DefinitionStmt) {
	    DefinitionStmt ds=(DefinitionStmt) s;
	    if(ds.getLeftOp() instanceof Local) {
		Local l=(Local) ds.getLeftOp();
		outSet.add(l);
	    }
	} 
    }

    protected void merge(Object in1,Object in2,Object out) {
	FlowSet outSet=(FlowSet) out;
	FlowSet inSet1=(FlowSet) in1;
	FlowSet inSet2=(FlowSet) in2;
	inSet1.intersection(inSet2,outSet);
    }

    protected void copy(Object source,Object dest) {
	FlowSet sourceSet=(FlowSet) source;
	FlowSet destSet=(FlowSet) dest;
	sourceSet.copy(destSet);
    }
	

}
