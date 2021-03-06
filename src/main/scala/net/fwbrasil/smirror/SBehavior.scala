package net.fwbrasil.smirror

import scala.reflect.runtime.universe._
import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException

trait SBehavior[C] extends Visibility[C] {
    implicit val runtimeMirror: Mirror
    val owner: SType[C]
    val symbol: MethodSymbol
    val name = symbol.name.toString.trim
    type SParameterType <: SParameter[C]
    val parametersSymbols =
        symbol.paramss.map(_.map(_.asTerm))
    val parametersGroups = {
        var paramIndex = 0
        def index = {
            val res = paramIndex
            paramIndex += 1
            res
        }
        parametersSymbols.map(_.map(sParameter(_, index)))
    }
    protected def sParameter(symbol: TermSymbol, index: Int): SParameterType
    val parameters = parametersGroups.flatten
    val typeSignature = symbol.returnType
    lazy val returnType = sClassOf(typeSignature)

    private lazy val toStringParameters =
        parametersGroups.map(_.mkString(", ")).mkString(")(")

    override lazy val toString =
        name + "(" + toStringParameters + "): " +
            returnType.name.trim

}

case class SConstructor[C](owner: SClass[C], symbol: MethodSymbol)(implicit val runtimeMirror: Mirror)
    extends SBehavior[C] {

    type SParameterType = SConstructorParameter[C]
    val mirror = owner.mirror.reflectConstructor(symbol)
    override protected def sParameter(symbol: TermSymbol, index: Int) =
        SConstructorParameter[C](this, symbol, index)
    def invoke(params: Any*) =
        mirror.apply(params: _*).asInstanceOf[C]
}

case class SMethod[C](owner: SType[C], symbol: MethodSymbol)(implicit val runtimeMirror: Mirror)
    extends SBehavior[C] with TypeParameters {

    val javaMethodOption = {
        runtimeMirror.getClass.getDeclaredMethods.find(_.getName == "methodToJava").flatMap { method =>
            try Some(method.invoke(runtimeMirror, symbol).asInstanceOf[Method])
            catch {
                case e: InvocationTargetException if (e.getCause.isInstanceOf[NoSuchMethodException]) =>
                    None
                case e: InvocationTargetException if (e.getCause.isInstanceOf[ClassNotFoundException]) =>
                    None
            }
        }
    }
    type SParameterType = SMethodParameter[C]
    override protected def sParameter(symbol: TermSymbol, index: Int) =
        SMethodParameter[C](this, symbol, index)
    def invoke(obj: C, params: Any*) = {
        val instanceMirror = runtimeMirror.reflect(obj: Any)
        val method = instanceMirror.reflectMethod(symbol)
        method(params: _*)
    }
}