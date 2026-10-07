package com.slimenull.customsidebuttonfunctions.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Runtime state shared by the LibXposed 102 entry point and hook helpers. */
internal object XposedRuntime {
    @Volatile
    var api: XposedInterface? = null

    fun bind(value: XposedInterface) {
        api = value
    }

    fun log(priority: Int, message: String, error: Throwable? = null) {
        val current = api
        if (current != null) {
            current.log(priority, "CustomSideButtonFunctions", message, error)
        } else {
            Log.println(priority, "CustomSideButtonFunctions", message)
            if (error != null) Log.e("CustomSideButtonFunctions", message, error)
        }
    }
}

/** Logger backed by the LibXposed 102 interface. */
internal object XposedBridge {
    fun log(message: String) = XposedRuntime.log(Log.INFO, message)
    fun log(error: Throwable) = XposedRuntime.log(Log.ERROR, error.message ?: error.javaClass.name, error)
}

/** Small Kotlin callback facade over the LibXposed 102 interceptor chain. */
abstract class Hooker {
    open fun beforeHookedMethod(param: HookParam) = Unit
    open fun afterHookedMethod(param: HookParam) = Unit

    class HookParam internal constructor(private val chain: XposedInterface.Chain) {
        val method: Executable get() = chain.executable
        val thisObject: Any get() = chain.thisObject!!
        val args: Array<Any?> = chain.args.toTypedArray()

        private var hasResult = false
        private var result: Any? = null

        fun setResult(value: Any?) {
            hasResult = true
            result = value
        }

        internal fun invoke(hook: Hooker): Any? {
            hook.beforeHookedMethod(this)
            if (!hasResult) result = chain.proceed()
            hook.afterHookedMethod(this)
            return result
        }
    }
}

/** Reflection lookup helpers; registration itself uses XposedInterface.hook. */
internal object XposedHelpers {
    fun findClass(name: String, classLoader: ClassLoader?): Class<*> = Class.forName(name, false, classLoader)

    fun findClassIfExists(name: String, classLoader: ClassLoader?): Class<*>? = try {
        findClass(name, classLoader)
    } catch (_: Throwable) {
        null
    }

    fun hookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any
    ) = hookMethod(findClass(className, classLoader), methodName, *parameterTypesAndCallback)

    fun hookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any
    ) {
        val callback = parameterTypesAndCallback.last() as Hooker
        val parameterTypes = parameterTypesAndCallback.dropLast(1).map { it as Class<*> }.toTypedArray()
        val method = findMethod(clazz, methodName, parameterTypes)
        hook(method, callback)
    }

    fun hookAllMethods(clazz: Class<*>, methodName: String, callback: Hooker) {
        allMethods(clazz, methodName).forEach { hook(it, callback) }
    }

    fun hookAllConstructors(clazz: Class<*>, callback: Hooker) {
        clazz.declaredConstructors.forEach { hook(it, callback) }
    }

    fun getObjectField(instance: Any, name: String): Any = findField(instance.javaClass, name).get(instance) ?: Unit

    fun getIntField(instance: Any, name: String): Int = findField(instance.javaClass, name).getInt(instance)

    fun callMethod(instance: Any, name: String, vararg args: Any?): Any {
        val method = allMethods(instance.javaClass, name)
            .firstOrNull { it.parameterTypes.size == args.size && it.parameterTypes.zip(args).all { (type, arg) -> accepts(type, arg) } }
            ?: error("$name(${args.size}) not found on ${instance.javaClass.name}")
        method.isAccessible = true
        return method.invoke(instance, *args) ?: Unit
    }

    private fun hook(executable: Executable, callback: Hooker) {
        val api = XposedRuntime.api ?: error("Xposed API is not bound")
        api.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain -> Hooker.HookParam(chain).invoke(callback) }
    }

    private fun findMethod(clazz: Class<*>, name: String, parameterTypes: Array<Class<*>>): Method =
        allMethods(clazz, name).firstOrNull { it.parameterTypes.contentEquals(parameterTypes) }
            ?: error("$name not found on ${clazz.name}")

    private fun allMethods(clazz: Class<*>, name: String): List<Method> =
        generateSequence(clazz) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.name == name }
            .toList()

    private fun findField(clazz: Class<*>, name: String): Field =
        generateSequence(clazz) { it.superclass }
            .mapNotNull {
                try {
                    it.getDeclaredField(name)
                } catch (_: NoSuchFieldException) {
                    null
                }
            }
            .firstOrNull()
            ?.also { it.isAccessible = true }
            ?: error("$name not found on ${clazz.name}")

    private fun accepts(type: Class<*>, value: Any?): Boolean {
        if (value == null) return !type.isPrimitive
        if (!type.isPrimitive) return type.isInstance(value)
        return when (type) {
            Boolean::class.javaPrimitiveType -> value is Boolean
            Byte::class.javaPrimitiveType -> value is Byte
            Short::class.javaPrimitiveType -> value is Short
            Int::class.javaPrimitiveType -> value is Int
            Long::class.javaPrimitiveType -> value is Long
            Float::class.javaPrimitiveType -> value is Float
            Double::class.javaPrimitiveType -> value is Double
            Char::class.javaPrimitiveType -> value is Char
            else -> false
        }
    }
}

internal data class PackageHookParam(
    val packageName: String,
    val classLoader: ClassLoader
)
