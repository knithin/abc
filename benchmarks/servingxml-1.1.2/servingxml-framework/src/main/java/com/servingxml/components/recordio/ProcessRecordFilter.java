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

package com.servingxml.components.recordio;

import com.servingxml.app.ServiceContext;
import com.servingxml.components.task.Task;
import com.servingxml.app.Flow;
import com.servingxml.util.Name;
import com.servingxml.util.record.Record;
import com.servingxml.app.Environment;

/**
 *
 * 
 * @author  Daniel A. Parker
 */


class ProcessRecordFilter extends AbstractRecordFilter {
  private final Environment env;
  private final Task[] tasks;
  private final Name recordTypeName;

  public ProcessRecordFilter(Environment env, Task[] tasks, Name recordTypeName) {
    this.env = env;
    this.tasks = tasks;
    this.recordTypeName = recordTypeName;
  }

  public void writeRecord(ServiceContext context, Flow flow) {
    flow = env.augmentParametersOf(context,flow);

    Record record = flow.getRecord();
    if (recordTypeName.isEmpty() || recordTypeName.equals(record.getRecordType().getName())) {

      for (int i = 0; i < tasks.length; ++i) {
        Task task = tasks[i];                                 
        task.execute(context, flow);
      }
    } 
    getRecordWriter().writeRecord(context, flow);
  }
}
