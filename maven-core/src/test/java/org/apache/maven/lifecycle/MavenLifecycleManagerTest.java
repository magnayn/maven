package org.apache.maven.lifecycle;

import org.apache.maven.MavenTestCase;
import org.apache.maven.lifecycle.goal.MavenGoalPhaseManager;

import java.util.List;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @version $Id$
 */
public class MavenLifecycleManagerTest
    extends MavenTestCase
{
    public void testMavenLifecycleManager()
        throws Exception
    {
        MavenGoalPhaseManager mlm = (MavenGoalPhaseManager) lookup( MavenGoalPhaseManager.ROLE );

        List lifecyclePhases = mlm.getLifecyclePhases();

        assertEquals( 6, lifecyclePhases.size() );
    }
}
