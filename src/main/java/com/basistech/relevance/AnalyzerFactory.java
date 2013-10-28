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

import com.google.common.collect.Lists;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.CharFilterFactory;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Factory class to build a Lucene analyzer at arms length. Sort of a bit like a tiny
 * Solr schema in a box. This fetches the analyzer code from Maven repos with Aether;
 * alternatives are obvious. There is a bit of an assumption of dependency-injection
 * here but the wiring shouldn't be to onerous for Spring-haters.  Lucene lacks a
 */
public class AnalyzerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AnalyzerFactory.class);
    private List<Repository> repositories = Lists.newArrayList();
    private List<String> artifacts;
    // note: caller is free to change these between obtaining analyzers!
    private ComponentSpec tokenizerSpec;
    private List<ComponentSpec> charFilterSpecs = Lists.newArrayList();
    private List<ComponentSpec> tokenFilterSpecs = Lists.newArrayList();

    public Analyzer newAnalyzer() {
        final TokenizerFactory tokenizerFactory = TokenizerFactory.forName(tokenizerSpec.getName(), tokenizerSpec.getOptions());
        final List<TokenFilterFactory> tokenFilterFactories = Lists.newArrayList();
        for (ComponentSpec spec : tokenFilterSpecs) {
            TokenFilterFactory tokenFilterFactory = TokenFilterFactory.forName(spec.getName(), spec.getOptions());
            tokenFilterFactories.add(tokenFilterFactory);
        }
        final List<CharFilterFactory> charFilterFactories = Lists.newArrayList();
        for (ComponentSpec spec : charFilterSpecs) {
            CharFilterFactory charFilterFactory = CharFilterFactory.forName(spec.getName(), spec.getOptions());
            charFilterFactories.add(charFilterFactory);
        }

        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
                for (CharFilterFactory charFilterFactory : charFilterFactories) {
                    reader = charFilterFactory.create(reader);
                }
                Tokenizer tokenizer = tokenizerFactory.create(reader);
                TokenStream filter = tokenizer;
                for (TokenFilterFactory tokenFilterFactory : tokenFilterFactories) {
                    filter = tokenFilterFactory.create(filter);

                }
                return new TokenStreamComponents(tokenizer, filter);
            }
        };
    }

    public void initialize() {
        List<URL> analyzerJars = gatherJarsFromMaven();

        ClassLoader componentClassLoader = new URLClassLoader(analyzerJars.toArray(new URL[analyzerJars.size()]));
        TokenizerFactory.reloadTokenizers(componentClassLoader);
        TokenFilterFactory.reloadTokenFilters(componentClassLoader);
        CharFilterFactory.reloadCharFilters(componentClassLoader);
    }

    private List<URL> gatherJarsFromMaven() {
        RepositorySystem system = AetherBooter.newRepositorySystem();

        DefaultRepositorySystemSession session = AetherBooter.newRepositorySystemSession(system);
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        //TODO: make this configurable. for now, here's the Basis mirror.
        /*
            <mirror>
      <id>Nexus</id>
      <name>Nexus Mirror</name>
      <url>http://maven.basistech.net/nexus/content/groups/public</url>
      <mirrorOf>*,!apache.org,!sonar,!apache.snapshots</mirrorOf>
    </mirror>
         */
        mirrorSelector = mirrorSelector.add("Nexus", "http://maven.basistech.net/nexus/content/groups/public", null, true, "*,!apache.org,!sonar,!apache.snapshots", null);
        session.setMirrorSelector(mirrorSelector);
        List<URL> analyzerJars = Lists.newArrayList();

        for (String artifactSpec : artifacts) {
            LOG.info("Collecting jars for {}", artifactSpec);
            Artifact artifact = new DefaultArtifact(artifactSpec);

            CollectRequest collectRequest = new CollectRequest();
            RemoteRepository repo = AetherBooter.newCentralRepository();
            collectRequest.addRepository(repo);
            for (Repository repoSpec : repositories) {
                collectRequest.addRepository(new RemoteRepository.Builder(repoSpec.getId(), "default", repoSpec.getUrl()).build());
            }

            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME, JavaScopes.COMPILE);
            collectRequest.setRoot(new Dependency(artifact, JavaScopes.COMPILE));

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

            List<ArtifactResult> artifactResults;
            try {
                artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();
            } catch (DependencyResolutionException e) {
                throw new RuntimeException(e);
            }


            for (ArtifactResult artifactResult : artifactResults) {
                LOG.info("Collecting {}", artifactResult.getArtifact().getFile().getAbsolutePath());
                try {
                    analyzerJars.add(artifactResult.getArtifact().getFile().toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return analyzerJars;
    }


    public List<Repository> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories;
    }

    public List<String> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<String> artifacts) {
        this.artifacts = artifacts;
    }

    public ComponentSpec getTokenizerSpec() {
        return tokenizerSpec;
    }

    public void setTokenizerSpec(ComponentSpec tokenizerSpec) {
        this.tokenizerSpec = tokenizerSpec;
    }

    public List<ComponentSpec> getCharFilterSpecs() {
        return charFilterSpecs;
    }

    public void setCharFilterSpecs(List<ComponentSpec> charFilterSpecs) {
        this.charFilterSpecs = charFilterSpecs;
    }

    public List<ComponentSpec> getTokenFilterSpecs() {
        return tokenFilterSpecs;
    }

    public void setTokenFilterSpecs(List<ComponentSpec> tokenFilterSpecs) {
        this.tokenFilterSpecs = tokenFilterSpecs;
    }
}
