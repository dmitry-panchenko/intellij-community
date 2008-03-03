/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Alexander Kireyev
 */
public class LoadingOrder {
  @NonNls private static final String FIRST_STR = "FIRST";
  @NonNls private static final String LAST_STR = "LAST";
  @NonNls private static final String BEFORE_STR = "BEFORE ";
  @NonNls private static final String AFTER_STR = "AFTER ";

  public static final LoadingOrder ANY = new LoadingOrder();
  public static final LoadingOrder FIRST = new LoadingOrder(FIRST_STR);
  public static final LoadingOrder LAST = new LoadingOrder(LAST_STR);

  @NonNls private final String myName; // for debug only
  private boolean myFirst;
  private boolean myLast;
  private final Set<String> myBefore = new HashSet<String>();
  private final Set<String> myAfter = new HashSet<String>();

  private LoadingOrder() {
    myName = "ANY";
  }

  private LoadingOrder(@NonNls @NotNull String text) {
    myName = text;
    final String[] strings = text.split(",");
    for (final String string : strings) {
      String trimmed = string.trim();
      if (trimmed.equalsIgnoreCase(FIRST_STR)) myFirst = true;
      else if (trimmed.equalsIgnoreCase(LAST_STR)) myLast = true;
      else if (StringUtil.startsWithIgnoreCase(trimmed, BEFORE_STR)) myBefore.add(trimmed.substring(BEFORE_STR.length()));
      else if (StringUtil.startsWithIgnoreCase(trimmed, AFTER_STR)) myAfter.add(trimmed.substring(AFTER_STR.length()));
      else throw new AssertionError("Invalid specification: " + trimmed + "; should be one of FIRST, LAST, BEFORE <id> or AFTER <id>");
    }

  }

  public String toString() {
    return myName;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof LoadingOrder)) return false;

    final LoadingOrder that = (LoadingOrder)o;

    if (myFirst != that.myFirst) return false;
    if (myLast != that.myLast) return false;
    if (!myAfter.equals(that.myAfter)) return false;
    if (!myBefore.equals(that.myBefore)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myFirst ? 1 : 0);
    result = 31 * result + (myLast ? 1 : 0);
    result = 31 * result + myBefore.hashCode();
    result = 31 * result + myAfter.hashCode();
    return result;
  }

  public static LoadingOrder before(@NonNls final String id) {
    return new LoadingOrder(BEFORE_STR + id);
  }

  public static LoadingOrder after(@NonNls final String id) {
    return new LoadingOrder(AFTER_STR + id);
  }

  public static void sort(final Orderable[] orderables) {
    final Map<String,Orderable> map = new HashMap<String, Orderable>();
    for (final Orderable orderable : orderables) {
      final String id = orderable.getOrderId();
      if (StringUtil.isNotEmpty(id)) {
        map.put(id, orderable);
      }
    }

    DFSTBuilder<Orderable> builder = new DFSTBuilder<Orderable>(new GraphGenerator<Orderable>(new CachingSemiGraph<Orderable>(new GraphGenerator.SemiGraph<Orderable>() {
      public Collection<Orderable> getNodes() {
        return Arrays.asList(orderables);
      }

      public Iterator<Orderable> getIn(final Orderable n) {
        final LoadingOrder order = n.getOrder();

        Set<Orderable> predecessors = new LinkedHashSet<Orderable>();
        for (final String id : order.myAfter) {
          final Orderable orderable = map.get(id);
          if (orderable != null) {
            predecessors.add(orderable);
          }
        }

        String id = n.getOrderId();
        for (final Orderable orderable : orderables) {
          final LoadingOrder hisOrder = orderable.getOrder();
          if (StringUtil.isNotEmpty(id) && hisOrder.myBefore.contains(id) ||
              order.myLast && !hisOrder.myLast ||
              hisOrder.myFirst && !order.myFirst) {
            predecessors.add(orderable);
          }
        }
        return predecessors.iterator();
      }
    })));

    if (!builder.isAcyclic()) {
      final Pair<Orderable,Orderable> dependency = builder.getCircularDependency();
      throw new SortingException("Could not satisfy sorting requirements", new Element[]{dependency.first.getDescribingElement(), dependency.second.getDescribingElement()});
    }

    Arrays.sort(orderables, builder.comparator());
  }

  public static LoadingOrder readOrder(@NonNls String orderAttr) {
    return orderAttr != null ? new LoadingOrder(orderAttr) : ANY;
  }

  public interface Orderable {
    @Nullable
    String getOrderId();
    LoadingOrder getOrder();
    Element getDescribingElement();
  }
}
