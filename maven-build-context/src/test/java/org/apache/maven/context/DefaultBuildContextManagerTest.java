package org.apache.maven.context;

public class DefaultBuildContextManagerTest
    extends AbstractBuildContextManagerTest
{

    protected String getRoleHintBeforeSetUp()
    {
        return DefaultBuildContextManager.ROLE_HINT;
    }

}
