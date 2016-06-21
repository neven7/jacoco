/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.runtime;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jacoco.core.JaCoCo;
import org.jacoco.core.internal.instr.Companions;
import org.jacoco.core.internal.instr.InstrSupport;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Strategy that creates "companion" classes in "offline" mode, which will hold
 * state of instrumented classes. This allows to avoid addition of members to
 * classes.
 */
public class OfflineInstrumentationCompanionAccessGenerator
		implements IExecutionDataAccessorGenerator {

	private final OfflineInstrumentationAccessGenerator generator;

	private final IClassFileWriter classFileWriter;

	private String companionName;

	private final Set<String> instrumented = new HashSet<String>();

	private ClassWriter cw;

	private MethodVisitor mv;

	/**
	 * Creates a new instance for "offline" instrumentation.
	 */
	public OfflineInstrumentationCompanionAccessGenerator(
			final IClassFileWriter classFileWriter) {
		this(JaCoCo.RUNTIMEPACKAGE.replace('.', '/') + "/Offline",
				classFileWriter);
	}

	/**
	 * Creates a new instance with the given runtime class name for testing
	 * purposes.
	 *
	 * @param runtimeClassName
	 *            VM name of the runtime class
	 */
	OfflineInstrumentationCompanionAccessGenerator(
			final String runtimeClassName,
			final IClassFileWriter classFileWriter) {
		this.generator = new OfflineInstrumentationAccessGenerator(
				runtimeClassName);
		this.classFileWriter = classFileWriter;
		newCompanion();
	}

	public int generateDataAccessor(final long classid, final String classname,
			final int probecount, final MethodVisitor mv) {
		if (instrumented.size() == Companions.FIELDS_PER_CLASS) {
			classFileWriter.write(getClassName(), getClassDefinition());
			newCompanion();
		}
		final String fieldName = fieldNameFor(classid);
		if (instrumented.add(classname)) {
			cw.visitField(
					Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
							| Opcodes.ACC_TRANSIENT | Opcodes.ACC_FINAL,
					fieldName, InstrSupport.DATAFIELD_DESC, null, null);
			generator.generateDataAccessor(classid, classname, probecount,
					this.mv);
			this.mv.visitFieldInsn(Opcodes.PUTSTATIC, companionName, fieldName,
					InstrSupport.DATAFIELD_DESC);
		}
		mv.visitFieldInsn(Opcodes.GETSTATIC, companionName, fieldName,
				InstrSupport.DATAFIELD_DESC);
		return 1;
	}

	/**
	 * Returns the number of instrumented classes since last invocation of
	 * {@link #getClassDefinition()}.
	 * 
	 * @return number of instrumented classes since last invocation of
	 *         {@link #getClassDefinition()}
	 */
	int getNumberOfInstrumentedClasses() {
		return instrumented.size();
	}

	/**
	 * Returns the name of generated "companion" class.
	 *
	 * @return name of generated "companion" class
	 */
	public String getClassName() {
		return companionName;
	}

	private void newCompanion() {
		instrumented.clear();
		companionName = Companions.COMPANION_NAME
				+ UUID.randomUUID().toString().replace('-', '_');

		cw = new ClassWriter(0);
		cw.visit(Opcodes.V1_1,
				Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
				companionName, null, "java/lang/Object", null);
		mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
	}

	/**
	 * Returns the bytecode of generated "companion" class.
	 *
	 * @return bytecode of generated "companion" class
	 */
	byte[] getClassDefinition() {
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(OfflineInstrumentationAccessGenerator.STACK_SIZE, 0);
		mv.visitEnd();
		cw.visitEnd();
		final byte[] result = cw.toByteArray();
		newCompanion();
		return result;
	}

	public void end() {
		if (instrumented.size() != 0) {
			classFileWriter.write(getClassName(), getClassDefinition());
		}
	}

	/**
	 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.2.2
	 */
	static String fieldNameFor(long classId) {
		return "p" + Long.toHexString(classId);
	}

}