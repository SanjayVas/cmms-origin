// Copyright 2023 The Cross-Media Measurement Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import React from 'react';
import { UniqueReach } from '../../../model/reporting';
import { Chart, ChartType } from '../chart';

type OnTargetUniqueReachByPlatProps = {
    id: string,
    reach: UniqueReach[],
    pubColors: { [Name: string]: string}
}

export function OnTargetUniqueReachByPlat({id, reach, pubColors}: OnTargetUniqueReachByPlatProps) {
    const config = {
        pubColors,
    }
    return (
        <Chart
            cardId={id}
            title='On target unique reach by platform'
            data={reach}
            config={config}
            type={ChartType.multiLine}
        />
    )
}
