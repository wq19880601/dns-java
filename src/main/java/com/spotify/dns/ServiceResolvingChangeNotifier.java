/*
 * Copyright (c) 2012-2015 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spotify.dns;

import com.google.common.base.Function;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An endpoint provider that resolves and provides tcp:// endpoints for a service using DNS. The
 * endpoints are refreshed at a configurable interval.
 */
class ServiceResolvingChangeNotifier<T> extends AbstractChangeNotifier<T>
    implements ChangeNotifierFactory.RunnableChangeNotifier<T> {

  private static final Logger log = LoggerFactory.getLogger(ServiceResolvingChangeNotifier.class);

  private final DnsSrvResolver resolver;
  private final String fqdn;
  private final Function<LookupResult, T> resultTransformer;

  private volatile Set<T> endpoints = Collections.emptySet();

//  private final ScheduledFuture<?> updaterFuture;

  /**
   * Create a {@link ChangeNotifier} that tracks changes from a {@link DnsSrvResolver}.
   *
   * The list of {@link LookupResult}s will be transformed using the provided function
   * and put into a set. The set will then be compared to the previous set and if a
   * change is detected, the notifier will fire.
   *
   * @param resolver            The resolver to use.
   * @param fqdn                The name to lookup SRV records for
   * @param resultTransformer   The transform function
   */
  ServiceResolvingChangeNotifier(final DnsSrvResolver resolver,
                                 final String fqdn,
                                 final Function<LookupResult, T> resultTransformer) {

    this.resolver = checkNotNull(resolver);
    this.fqdn = checkNotNull(fqdn, "fqdn");
    this.resultTransformer = checkNotNull(resultTransformer, "resultTransformer");
  }

  @Override
  protected void closeImplementation() {
    // TODO: resolve
//    updaterFuture.cancel(false);
  }

  @Override
  public Set<T> current() {
    return Collections.unmodifiableSet(endpoints);
  }

  @Override
  public void run() {
    try {
      List<LookupResult> nodes = resolver.resolve(fqdn);
      Set<T> currentEndpoints = Sets.newHashSet();
      for (LookupResult node : nodes) {
        currentEndpoints.add(resultTransformer.apply(node));
      }

      if (!currentEndpoints.equals(endpoints)) {
        final ChangeNotification<T> changeNotification =
            newChangeNotification(currentEndpoints, endpoints);
        endpoints = currentEndpoints;

        fireEndpointsUpdated(changeNotification);
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }
}

