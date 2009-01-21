package org.apache.maven.project.builder.rules;

import org.apache.maven.shared.model.*;
import org.apache.maven.shared.model.impl.DefaultModelDataSource;
import org.apache.maven.project.builder.ProjectUri;
import org.apache.maven.project.builder.AlwaysJoinModelContainerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;

public class ExecutionRule implements ModelContainerRule {

    public List<ModelProperty> execute(List<ModelProperty> modelProperties) {
        List<ModelProperty> properties = new ArrayList<ModelProperty>(modelProperties);
        List<ModelProperty> goalProperties = new ArrayList<ModelProperty>();
        List<ModelProperty> processedProperties = new ArrayList<ModelProperty>();

        for(ModelProperty mp : properties) {
            if(mp.getUri().equals(ProjectUri.Build.Plugins.Plugin.Executions.Execution.Goals.goal)) {
                goalProperties.add(mp);
            } else if(!containsProperty(mp, processedProperties)) {
                processedProperties.add(mp);
            }
        }
        
        if(!goalProperties.isEmpty()) {
            Collections.reverse(goalProperties);
            List<ModelProperty> uniqueGoals = new ArrayList<ModelProperty>();
            for(ModelProperty mp : goalProperties) {
                if(!containsProperty(mp, uniqueGoals)) {
                    uniqueGoals.add(mp);
                }
            }
            Collections.reverse(uniqueGoals);

            processedProperties.addAll(
                    findIndexOf(ProjectUri.Build.Plugins.Plugin.Executions.Execution.Goals.xURI, processedProperties) + 1,
                    uniqueGoals);
        }

        List<ModelProperty> emptyTags = new ArrayList<ModelProperty>();
        for(ModelProperty mp : processedProperties) {
            if(mp.getUri().equals(ProjectUri.Build.Plugins.Plugin.Executions.Execution.Goals.xURI)
                    && mp.getResolvedValue() != null && mp.getResolvedValue().trim().equals("")) {
                emptyTags.add(mp);
            }
        }
        processedProperties.removeAll(emptyTags);

        return processedProperties;
    }


    private static int findIndexOf(String uri, List<ModelProperty> modelProperties) {
        for(ModelProperty mp : modelProperties) {
            if(mp.getUri().equals(uri)) {
                return modelProperties.indexOf(mp);
            }
        }
        return -1;
    }

    private static boolean containsProperty(ModelProperty modelProperty, List<ModelProperty> modelProperties) {
        for (ModelProperty mp : modelProperties) {
            if ((mp.getUri().equals(modelProperty.getUri()))) {
                boolean b = (mp.getResolvedValue() == null && modelProperty.getResolvedValue() == null) ||
                        (mp.getResolvedValue() != null && !mp.getResolvedValue().trim().equals("")
                                && mp.getResolvedValue().equals(modelProperty.getResolvedValue()));
                /*
                                boolean b = (mp.getResolvedValue() == null && modelProperty.getResolvedValue() == null) ||
                        (mp.getResolvedValue() != null && modelProperty.getResolvedValue() != null
                                && mp.getResolvedValue().equals(modelProperty.getResolvedValue()));
                 */
                if(b) {
                    return true;
                }
            }
        }
        return false;
    }
}
