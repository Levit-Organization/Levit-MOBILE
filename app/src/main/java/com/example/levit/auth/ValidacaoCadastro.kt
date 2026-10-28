package com.example.levit.auth

/**
 * Regras do cadastro, iguais às de client-frontend/src/pages/Register.jsx e
 * utils/masks.js, sem dependências de UI. A senha segue o POST /auth/registrar
 * (mínimo 8, letras e números), o mesmo critério da redefinição.
 */
object ValidacaoCadastro {

    private fun soDigitos(valor: String) = valor.filter { it in '0'..'9' }

    /** CPF (até 11 dígitos) ou CNPJ (até 14), formatado enquanto digita. */
    fun mascararCpfCnpj(valor: String): String {
        val d = soDigitos(valor).take(14)
        if (d.length <= 11) {
            return d
                .replaceFirst(Regex("^(\\d{3})(\\d)"), "$1.$2")
                .replaceFirst(Regex("^(\\d{3})\\.(\\d{3})(\\d)"), "$1.$2.$3")
                .replaceFirst(Regex("\\.(\\d{3})(\\d)"), ".$1-$2")
        }
        return d
            .replaceFirst(Regex("^(\\d{2})(\\d)"), "$1.$2")
            .replaceFirst(Regex("^(\\d{2})\\.(\\d{3})(\\d)"), "$1.$2.$3")
            .replaceFirst(Regex("\\.(\\d{3})(\\d)"), ".$1/$2")
            .replaceFirst(Regex("(\\d{4})(\\d)"), "$1-$2")
    }

    /**
     * O web só confere a quantidade de dígitos (11 ou 14). Aqui também se
     * confere o dígito verificador, para pegar erro de digitação antes de
     * criar a empresa com um documento que não existe.
     */
    fun documentoValido(valor: String): Boolean {
        val d = soDigitos(valor)
        return when (d.length) {
            11 -> cpfValido(d)
            14 -> cnpjValido(d)
            else -> false
        }
    }

    fun senhaValida(senha: String): Boolean = ValidacaoRedefinicaoSenha.senhaValida(senha)

    /** 0 a 4, mesma conta do calcularForcaSenha do Register.jsx. */
    fun forca(senha: String): Int {
        var forca = 0
        if (senha.length >= 8) forca++
        if (senha.any { it in 'A'..'Z' }) forca++
        if (senha.any { it in '0'..'9' }) forca++
        if (Regex("[^A-Za-z0-9]").containsMatchIn(senha)) forca++
        return forca
    }

    private fun cpfValido(cpf: String): Boolean {
        if (cpf.all { it == cpf[0] }) return false
        val n = cpf.map { it - '0' }
        val dv1 = digitoMod11(n.take(9), (10 downTo 2).toList())
        val dv2 = digitoMod11(n.take(10), (11 downTo 2).toList())
        return n[9] == dv1 && n[10] == dv2
    }

    private fun cnpjValido(cnpj: String): Boolean {
        if (cnpj.all { it == cnpj[0] }) return false
        val n = cnpj.map { it - '0' }
        val pesos1 = listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val dv1 = digitoMod11(n.take(12), pesos1)
        val dv2 = digitoMod11(n.take(13), listOf(6) + pesos1)
        return n[12] == dv1 && n[13] == dv2
    }

    private fun digitoMod11(digitos: List<Int>, pesos: List<Int>): Int {
        val resto = digitos.zip(pesos).sumOf { (d, p) -> d * p } % 11
        return if (resto < 2) 0 else 11 - resto
    }
}
