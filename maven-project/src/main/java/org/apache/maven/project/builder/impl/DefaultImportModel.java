package org.apache.maven.project.builder.impl;

import org.apache.maven.shared.model.ImportModel;
import org.apache.maven.shared.model.ModelProperty;

import java.util.List;
import java.util.ArrayList;

public final class DefaultImportModel implements ImportModel {

    private final String id;

    private final List<ModelProperty> modelProperties;

    public DefaultImportModel(String id, List<ModelProperty> modelProperties) {
        if(id == null)
        {
            throw new IllegalArgumentException("id: null");
        }
        
        if(modelProperties == null) {
            throw new IllegalArgumentException("modelProperties: null");
        }
        this.id = id;
        this.modelProperties = new ArrayList<ModelProperty>(modelProperties);
    }

    public String getId() {
        return id;
    }

    public List<ModelProperty> getModelProperties() {
        return new ArrayList<ModelProperty>(modelProperties);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImportModel that = (ImportModel) o;

        if (id != null ? !id.equals(that.getId()) : that.getId() != null) return false;

        return true;
    }

    public int hashCode() {
        return (id != null ? id.hashCode() : 0);
    }
}
