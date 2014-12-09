/*
 * Copyright (C) 2014 Google, Inc.
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
package com.squareup.javawriter;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

public final class EnumWriter extends TypeWriter {
  public static EnumWriter forClassName(ClassName name) {
    checkArgument(name.enclosingSimpleNames().isEmpty(), "%s must be top-level type.", name);
    return new EnumWriter(name);
  }

  private final Map<String, ConstantWriter> constantWriters = Maps.newLinkedHashMap();
  private final List<MethodWriter> constructorWriters = Lists.newArrayList();

  EnumWriter(ClassName name) {
    super(name);
  }

  public ConstantWriter addConstant(String name) {
    ConstantWriter constantWriter = new ConstantWriter(name);
    constantWriters.put(name, constantWriter);
    return constantWriter;
  }

  public MethodWriter addConstructor() {
    MethodWriter constructorWriter =
        new MethodWriter(Optional.<TypeName>absent(), name.simpleName());
    constructorWriters.add(constructorWriter);
    return constructorWriter;
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    context = createSubcontext(context);
    writeAnnotations(appendable, context);
    writeModifiers(appendable).append("enum ").append(name.simpleName());
    Writables.Joiner.on(", ").prefix(" implements ")
        .appendTo(appendable, context, implementedTypes);
    appendable.append(" {");

    checkState(!constantWriters.isEmpty(), "Cannot write an enum with no constants.");
    appendable.append('\n');
    ImmutableList<ConstantWriter> constantWriterList =
        ImmutableList.copyOf(constantWriters.values());
    for (ConstantWriter constantWriter
        : constantWriterList.subList(0, constantWriterList.size() - 1)) {
      constantWriter.write(appendable, context);
      appendable.append(",\n");
    }
    constantWriterList.get(constantWriterList.size() - 1).write(appendable, context);
    appendable.append(";\n");

    if (!fieldWriters.isEmpty()) {
      appendable.append('\n');
    }
    for (VariableWriter fieldWriter : fieldWriters.values()) {
      fieldWriter.write(new IndentingAppendable(appendable), context).append("\n");
    }
    for (MethodWriter constructorWriter : constructorWriters) {
      appendable.append('\n');
      if (!isDefaultConstructor(constructorWriter)) {
        constructorWriter.write(new IndentingAppendable(appendable), context);
      }
    }
    for (MethodWriter methodWriter : methodWriters) {
      appendable.append('\n');
      methodWriter.write(new IndentingAppendable(appendable), context);
    }
    for (TypeWriter nestedTypeWriter : nestedTypeWriters) {
      appendable.append('\n');
      nestedTypeWriter.write(new IndentingAppendable(appendable), context);
    }
    appendable.append("}\n");
    return appendable;
  }

  private static final Set<Modifier> VISIBILIY_MODIFIERS =
      Sets.immutableEnumSet(PUBLIC, PROTECTED, PRIVATE);

  private boolean isDefaultConstructor(MethodWriter constructorWriter) {
    return Sets.intersection(VISIBILIY_MODIFIERS, modifiers)
        .equals(Sets.intersection(VISIBILIY_MODIFIERS, constructorWriter.modifiers))
        && constructorWriter.body().isEmpty();
  }

  @Override
  public Set<ClassName> referencedClasses() {
    @SuppressWarnings("unchecked")
    Iterable<? extends HasClassReferences> concat =
        Iterables.concat(nestedTypeWriters, constantWriters.values(), fieldWriters.values(),
            constructorWriters, methodWriters, implementedTypes, annotations);
    return FluentIterable.from(concat)
        .transformAndConcat(GET_REFERENCED_CLASSES)
        .toSet();
  }

  public static final class ConstantWriter implements Writable, HasClassReferences {
    private final String name;
    private final List<Snippet> constructorSnippets;

    private ConstantWriter(String name) {
      this.name = name;
      this.constructorSnippets = Lists.newArrayList();
    }

    public ConstantWriter addArgument(Snippet snippet) {
      constructorSnippets.add(snippet);
      return this;
    }

    @Override
    public Appendable write(Appendable appendable, Context context) throws IOException {
      appendable.append(name);
      Writables.Joiner.on(", ").wrap("(", ")").appendTo(appendable, context, constructorSnippets);
      return appendable;
    }

    @Override
    public Set<ClassName> referencedClasses() {
      return FluentIterable.from(constructorSnippets)
          .transformAndConcat(GET_REFERENCED_CLASSES)
          .toSet();
    }
  }
}
