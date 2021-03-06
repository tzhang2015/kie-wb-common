/**
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.workbench.common.screens.datamodeller.events;

import org.guvnor.common.services.project.model.Project;
import org.jboss.errai.common.client.api.annotations.Portable;
import org.kie.workbench.common.screens.datamodeller.model.DataModelTO;
import org.kie.workbench.common.screens.datamodeller.model.DataObjectTO;

@Portable
public class DataObjectCreatedEvent extends DataModelerEvent {

    public DataObjectCreatedEvent() {
    }

    //TODO review if this constructor should be maintained
    public DataObjectCreatedEvent(String source, DataModelTO currentModel, DataObjectTO currentDataObject) {
        super(source, currentModel, currentDataObject);
    }

    public DataObjectCreatedEvent( Project currentProject, DataObjectTO currentDataObject ) {
        super( currentProject, currentDataObject );
    }
}
