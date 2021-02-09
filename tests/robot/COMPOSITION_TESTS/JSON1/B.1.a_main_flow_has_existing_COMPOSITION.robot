# Copyright (c) 2019 Wladislaw Wagner (Vitasystems GmbH), Pablo Pazos (Hannover Medical School).
#
# This file is part of Project EHRbase
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.



*** Settings ***
Documentation       Composition Integration Tests
Metadata            TOP_TEST_SUITE    COMPOSITION
Resource            ${CURDIR}${/}../../_resources/suite_settings.robot

Force Tags



*** Test Cases ***
Main flow has existing COMPOSITION (JSON)

    upload OPT    minimal/minimal_observation.opt
    create EHR
    commit composition (JSON)    minimal/minimal_observation.composition.participations.extdatetimes.xml
    get composition by composition_uid    ${version_uid}
    check composition exists

    [Teardown]    restart SUT


Main flow has existing COMPOSITION and works without accept header

    upload OPT    minimal/minimal_observation.opt
    create EHR
    commit composition without accept header    minimal/minimal_observation.composition.participations.extdatetimes.xml
    get composition by composition_uid    ${version_uid}
    check composition exists

    [Teardown]    restart SUT
