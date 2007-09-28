package org.jastadd.plugin.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.jastadd.plugin.AST.IFoldingNode;
import org.jastadd.plugin.AST.IJastAddNode;
import org.jastadd.plugin.editor.highlight.JastAddAutoIndentStrategy;
import org.jastadd.plugin.editor.hover.JastAddTextHover;
import org.jastadd.plugin.providers.JastAddContentProvider;
import org.jastadd.plugin.providers.JastAddLabelProvider;

public class JastAddEditorConfiguration {
	
	protected JastAddModel model;
	
	public JastAddEditorConfiguration() {
		this.model = null;
	}
	
	public JastAddEditorConfiguration(JastAddModel model) {
		this.model = model;
	}
	
	
	// No default insertion tactics after newline is provided
	public void getDocInsertionAfterNewline(IDocument doc, DocumentCommand cmd) {
	}
	
	// No default insertion on keypress is provided
	public void getDocInsertionOnKeypress(IDocument doc, DocumentCommand cmd) {
	}
	

	// No default syntax highlighting is provided
	public ITokenScanner getScanner() {
		return null;
	}

		// No default is provided
	public IContentAssistProcessor getCompletionProcessor() {
		return null;
	}
	
	// Uses attribute values from ContentOutline.jrag
	public IContentProvider getContentProvider() {
		return new JastAddContentProvider();
	}

	// Uses attribute values from ContentOutline.jrag
	public IBaseLabelProvider getLabelProvider() {
		return new JastAddLabelProvider();
	}

	// Uses attribute values from Hover.jrag
	public ITextHover getTextHover() {
		return new JastAddTextHover(model);
	}
	
	// Uses attribute values from Folding.jrag
	public List<Position> getFoldingPositions(IDocument document) {
		try {
			IJastAddNode node = model.getTreeRoot(document);
			if (node != null && node instanceof IFoldingNode) {
				return ((IFoldingNode)node).foldingPositions(document);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new ArrayList<Position>();
	}
	
	
	// Override getDocInsertionAfterNewline(IDocument doc, DocumentCommand cmd)
	// or getDocInsertionOnKeypress(IDocument doc, DocumentCommand cmd) before
	// considering to override this method
	public IAutoEditStrategy getAutoIndentStrategy() {
		return new JastAddAutoIndentStrategy(model);
	}
	
	public String getEditorContextID() {
		return "org.jastadd.plugin.JastAddEditorContext";
	}
	
	public String getErrorMarkerID() {
		return "org.eclipse.ui.workbench.texteditor.error";
	}
	
	public String getWarningMarkerID() {
		return "org.eclipse.ui.workbench.texteditor.warning";
	}
}
