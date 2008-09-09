package org.apache.maven.profiles.activation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.realm.MavenRealmManager;

import java.util.List;
import java.util.Properties;

public interface ProfileActivationContext
{

    List getExplicitlyActiveProfileIds();

    List getExplicitlyInactiveProfileIds();

    MavenRealmManager getRealmManager();

    Properties getExecutionProperties();

    boolean isCustomActivatorFailureSuppressed();

    void setCustomActivatorFailureSuppressed( boolean suppressed );

    void setExplicitlyActiveProfileIds( List inactive );

    void setExplicitlyInactiveProfileIds( List inactive );

    void setActive( String profileId );

    void setInactive( String profileId );

    boolean isExplicitlyActive( String profileId );

    boolean isExplicitlyInactive( String profileId );

    List getActiveByDefaultProfileIds();

    void setActiveByDefaultProfileIds( List activeByDefault );

    void setActiveByDefault( String profileId );

    boolean isActiveByDefault( String profileId );

}
