package org.apache.maven.settings.validation;

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

import org.apache.maven.settings.Settings;

/**
 * Validate a model settings
 *
 * @author Milos Kleint
 */
public interface SettingsValidator
{
    String ROLE = SettingsValidator.class.getName();

    /**
     * Validate a model settings
     *
     * @param model
     * @return a <code>SettingsValidationResult</code> instance
     */
    SettingsValidationResult validate( Settings model );
}
