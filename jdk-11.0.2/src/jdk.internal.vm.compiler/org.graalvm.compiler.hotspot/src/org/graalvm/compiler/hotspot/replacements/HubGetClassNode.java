/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConvertNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Read {@code Klass::_java_mirror} and incorporate non-null type information into stamp. This is
 * also used by {@link ClassGetHubNode} to eliminate chains of {@code klass._java_mirror._klass}.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class HubGetClassNode extends FloatingNode implements Lowerable, Canonicalizable, ConvertNode {
    public static final NodeClass<HubGetClassNode> TYPE = NodeClass.create(HubGetClassNode.class);
    @Input protected ValueNode hub;

    public HubGetClassNode(@InjectedNodeParameter MetaAccessProvider metaAccess, ValueNode hub) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createWithoutAssumptions(metaAccess.lookupJavaType(Class.class))));
        this.hub = hub;
    }

    public ValueNode getHub() {
        return hub;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        } else {
            MetaAccessProvider metaAccess = tool.getMetaAccess();
            if (metaAccess != null && hub.isConstant() && !GraalOptions.ImmutableCode.getValue(tool.getOptions())) {
                ResolvedJavaType exactType = tool.getConstantReflection().asJavaType(hub.asConstant());
                if (exactType != null) {
                    return ConstantNode.forConstant(tool.getConstantReflection().asJavaClass(exactType), metaAccess);
                }
            }
            return this;
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @NodeIntrinsic
    public static native Class<?> readClass(KlassPointer hub);

    @Override
    public ValueNode getValue() {
        return hub;
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return c;
        }
        return constantReflection.asJavaClass(constantReflection.asJavaType(c));
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return c;
        }
        ResolvedJavaType type = constantReflection.asJavaType(c);
        if (type.isPrimitive()) {
            return JavaConstant.NULL_POINTER;
        } else {
            return constantReflection.asObjectHub(type);
        }
    }

    /**
     * Any concrete Klass* has a corresponding {@link java.lang.Class}.
     */
    @Override
    public boolean isLossless() {
        return true;
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return true;
    }
}
