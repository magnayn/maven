package org.apache.maven.it0024;

public class Person
{
    private String name;
    
    public void setName( String name )
    {
        this.name = name;

        assert true;
    }
    
    public String getName()
    {
        return name;
    }
}
