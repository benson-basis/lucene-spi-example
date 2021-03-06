/******************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp.  It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2013 Basis Technology Corporation All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ******************************************************************************/

package com.basistech.relevance;

import java.util.Map;

/**
 * Facts about a Lucene component.
 */
public class ComponentSpec {
    private final String name;
    private final Map<String, String> options;

    public ComponentSpec(String name, Map<String, String> options) {
        this.name = name;
        this.options = options;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getOptions() {
        return options;
    }
}
