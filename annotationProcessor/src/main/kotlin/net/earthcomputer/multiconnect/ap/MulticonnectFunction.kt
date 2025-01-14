package net.earthcomputer.multiconnect.ap

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.lang.model.type.TypeMirror

@Serializable
data class MulticonnectFunction(
    val name: String,
    val returnType: TypeMirror,
    val positionalParameters: List<TypeMirror>,
    val parameters: List<MulticonnectParameter>,
    val possibleReturnTypes: List<TypeMirror>?,
)

@Serializable
sealed class MulticonnectParameter {
    abstract val paramType: TypeMirror

    @Serializable
    @SerialName("argument")
    class Argument(override val paramType: TypeMirror, val name: String, val translate: Boolean): MulticonnectParameter()
    @Serializable
    @SerialName("defaultConstructed")
    class DefaultConstructed(override val paramType: TypeMirror): MulticonnectParameter()
    @Serializable
    @SerialName("suppliedDefaultConstructed")
    class SuppliedDefaultConstructed(override val paramType: TypeMirror, val suppliedType: TypeMirror): MulticonnectParameter()
    @Serializable
    @SerialName("filled")
    class Filled(
        override val paramType: TypeMirror,
        @Contextual val fromRegistry: FilledArgument.FromRegistry?,
        val fromVersion: Int?,
        val toVersion: Int?,
    ): MulticonnectParameter()

    @Serializable
    @SerialName("globalData")
    class GlobalData(override val paramType: TypeMirror): MulticonnectParameter()
}
