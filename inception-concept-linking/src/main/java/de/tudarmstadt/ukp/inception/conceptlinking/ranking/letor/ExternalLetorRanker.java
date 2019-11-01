/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.conceptlinking.ranking.letor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.inception.conceptlinking.ranking.Ranker;
import de.tudarmstadt.ukp.inception.kb.graph.KBHandle;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class ExternalLetorRanker
    implements Ranker
{

    private static final String URL = "http://localhost:5000/rank";
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public List<KBHandle> rank(String aQuery, String aMention, Set<KBHandle> aCandidates,
                               CAS aCas, int aBeginOffset)
    {
        String aContext = getContext(aCas, aBeginOffset);

        List<KBHandle> unsortedCandidates = new ArrayList<>(aCandidates);
        PredictionRequest request = new PredictionRequest(aMention, aContext, unsortedCandidates);
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        OkHttpClient client = new OkHttpClient();

        okhttp3.RequestBody body = RequestBody.create(JSON, request.toJson());
        okhttp3.Request httpRequest = new okhttp3.Request.Builder()
                .url(URL)
                .post(body)
                .build();

        try {
            okhttp3.Response response = client.newCall(httpRequest).execute();
        } catch (IOException e) {
            log.error("Exception while re-ranking externally", e);
        }

        return new ArrayList<>(aCandidates);
    }

    private String getContext(CAS aCas, int aOffset) {
        if (aCas == null) {
            return "";
        }

        AnnotationFS sentence = WebAnnoCasUtil.selectSentenceAt(aCas, aOffset);
        if (sentence == null) {
            return "";
        }

        return sentence.getCoveredText();
    }
}