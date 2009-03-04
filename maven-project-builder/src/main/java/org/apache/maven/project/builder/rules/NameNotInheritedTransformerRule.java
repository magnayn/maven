package org.apache.maven.project.builder.rules;

import org.apache.maven.project.builder.TransformerRemovalRule;
import org.apache.maven.project.builder.ProjectUri;
import org.apache.maven.shared.model.ModelProperty;
import org.apache.maven.shared.model.DataSourceException;

import java.util.List;
import java.util.ArrayList;

/**
 * Do not inherit the name attribute of the pom
 */
public class NameNotInheritedTransformerRule implements TransformerRemovalRule
{
    public List<ModelProperty> executeWithReturnPropertiesToRemove(List<ModelProperty> modelProperties, boolean isMostSpecialized)
            throws DataSourceException
    {
        List<ModelProperty> removedProperties = new ArrayList<ModelProperty>();
        if ( !isMostSpecialized )
        {
            for ( ModelProperty mp : modelProperties )
            {
                if ( mp.getUri().startsWith( ProjectUri.name ) )
                {
                    removedProperties.add( mp );
                    return removedProperties;
                }
            }
        }
        return removedProperties;
    }
}
