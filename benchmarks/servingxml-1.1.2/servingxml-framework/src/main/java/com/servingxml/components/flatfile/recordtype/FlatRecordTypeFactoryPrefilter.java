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

import java.util.List;

import com.servingxml.app.ParameterDescriptor;
import com.servingxml.app.ServiceContext;
import com.servingxml.app.Flow;
import com.servingxml.components.flatfile.options.FlatFileOptions;

public class FlatRecordTypeFactoryPrefilter implements FlatRecordTypeFactory {
  private final FlatRecordTypeFactory flatRecordTypeFactory;
  private final ParameterDescriptor[] parameterDescriptors;

  public FlatRecordTypeFactoryPrefilter(FlatRecordTypeFactory flatRecordTypeFactory,
    ParameterDescriptor[] parameterDescriptors) {
    this.flatRecordTypeFactory = flatRecordTypeFactory;
    this.parameterDescriptors = parameterDescriptors;
  }

  public FlatRecordType createFlatRecordType(ServiceContext context, Flow flow, FlatFileOptions defaultOptions) {
    FlatRecordType flatRecordType = flatRecordTypeFactory.createFlatRecordType(context, flow, defaultOptions);
    return new FlatRecordTypePrefilter(flatRecordType, parameterDescriptors); 
  }

  public void appendFlatRecordField(ServiceContext context, Flow flow, 
    FlatFileOptions defaultOptions, List<FlatRecordField> flatRecordFieldList) {
    flatRecordTypeFactory.appendFlatRecordField(context, flow, defaultOptions, flatRecordFieldList);
  }

  public boolean isFieldDelimited() {
    return flatRecordTypeFactory.isFieldDelimited();
  }

  public boolean isBinary() {
    return flatRecordTypeFactory.isBinary();
  }

  public boolean isText() {
    return flatRecordTypeFactory.isText();
  }
}