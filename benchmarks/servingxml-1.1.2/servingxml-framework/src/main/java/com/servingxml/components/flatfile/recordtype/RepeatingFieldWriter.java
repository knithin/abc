/**
 *  ServingXML
 *  
 *  Copyright (C) 2006  Daniel Parker
 *    daniel.parker@servingxml.com 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 * 
 **/

package com.servingxml.components.flatfile.recordtype;

import com.servingxml.app.Flow;
import com.servingxml.app.ServiceContext;
import com.servingxml.components.flatfile.RecordOutput;
import com.servingxml.components.flatfile.options.Delimiter;
import com.servingxml.components.flatfile.options.FlatFileOptions;
import com.servingxml.components.quotesymbol.QuoteSymbol;
import com.servingxml.components.parameter.DefaultValue;
import com.servingxml.util.Formatter;
import com.servingxml.util.Name;
import com.servingxml.util.record.Record;

public class RepeatingFieldWriter implements FlatRecordFieldWriter {
  private final int start;
  private final Formatter fieldFormatter;
  private final DefaultValue defaultValueEvaluator;
  private final FlatFileOptions flatFileOptions;
  private final Delimiter fieldDelimiter;
  private final StringBuilder buf;

  public RepeatingFieldWriter(int start, Formatter fieldFormatter, 
    DefaultValue defaultValueEvaluator, FlatFileOptions flatFileOptions) {
    this.start = start;
    this.fieldFormatter = fieldFormatter;
    this.defaultValueEvaluator = defaultValueEvaluator;
    this.flatFileOptions = flatFileOptions;
    this.buf = new StringBuilder();
    Delimiter[] fieldDelimiters = flatFileOptions.getFieldDelimiters();
    this.fieldDelimiter = fieldDelimiters.length == 0 ? Delimiter.NULL : fieldDelimiters[0];
  }

  public void writeField(ServiceContext context, Flow flow, RecordOutput recordOutput) {
    writeField(context, flow, Name.EMPTY, recordOutput);
  }

  public void writeField(ServiceContext context, Flow flow, Name fieldName, RecordOutput recordOutput) {
    int offset = flatFileOptions.rebaseIndex(start);
    if (offset >= 0) {
      recordOutput.setPosition(offset);
    }

    Record record = flow.getRecord();

    buf.setLength(0);

    String v = record.getString(fieldName);
    if (v == null) {
      v = defaultValueEvaluator.evaluateString(context, flow);
    }
    String value = fieldFormatter.format(v);

    final QuoteSymbol quoteSymbol = flatFileOptions.getQuoteSymbol();

    boolean insertQuotes = flatFileOptions.useQuotes(value);
    if (insertQuotes) {
      buf.append(quoteSymbol.getCharacter());
      quoteSymbol.escape(value, buf);
      buf.append(quoteSymbol.getCharacter());
    } else {
      buf.append(value); 
    }
    recordOutput.writeString(buf.toString());
  }

  public void writeEndDelimiterTo(RecordOutput recordOutput) {
    //System.out.println(getClass().getName()+".writeEndDelimiterTo " + fieldDelimiter);
    fieldDelimiter.writeEndDelimiterTo(recordOutput);
  }
}
