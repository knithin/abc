package org.jastadd.plugin.jastaddj.model.repair;

import org.jastadd.plugin.model.repair.Interval;
import org.jastadd.plugin.model.repair.LexicalNode;
import org.jastadd.plugin.model.repair.Reef;

public abstract class JavaKeyword extends Reef {
	public JavaKeyword(LexicalNode previous, Interval interval, String value) {
		super(previous, interval, value);
	}
}
