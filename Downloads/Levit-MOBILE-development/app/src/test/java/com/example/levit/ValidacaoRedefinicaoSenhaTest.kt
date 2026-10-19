package com.example.levit

import com.example.levit.auth.ValidacaoRedefinicaoSenha
import org.junit.Assert.*
import org.junit.Test

class ValidacaoRedefinicaoSenhaTest {
    private val token = "a".repeat(64)

    @Test fun aceitaSenhaMinimaPermitidaPelaApi() {
        assertTrue(ValidacaoRedefinicaoSenha.senhaValida("abcde123"))
        assertTrue(ValidacaoRedefinicaoSenha.senhaValida("NovaSenha123!"))
    }

    @Test fun rejeitaSenhasIncompativeisComServidor() {
        listOf("Aa1!", "abcdefgh", "12345678", "Nova123\n").forEach {
            assertFalse(it, ValidacaoRedefinicaoSenha.senhaValida(it))
        }
    }

    @Test fun senhaCurtaNaoRecebeIndicacaoDeBoaOuForte() {
        assertTrue(ValidacaoRedefinicaoSenha.forca("Aa1!") < 3)
        assertEquals(0, ValidacaoRedefinicaoSenha.forca(""))
        assertEquals(4, ValidacaoRedefinicaoSenha.forca("NovaSenha123!"))
    }

    @Test fun extraiTokenDoDestinoDoAplicativo() {
        assertEquals(token, ValidacaoRedefinicaoSenha.tokenDoLink("levit://reset-password?token=$token"))
        assertEquals(token, ValidacaoRedefinicaoSenha.tokenDoLink("levit://reset-password/?token=$token"))
    }

    @Test fun rejeitaLinksAusentesIncompletosOuDeOutroDestino() {
        listOf(null, "", "http://exemplo.test/reset-password?token=$token",
            "levit://outro?token=$token", "levit://reset-password/outro?token=$token",
            "levit://reset-password?token=curto", "levit://reset-password",
            "levit://pessoa@reset-password?token=$token", "levit://reset-password:80?token=$token",
            "levit://reset-password?token=$token#outro", "levit://reset-password?token=%XX"
        ).forEach { assertNull(it, ValidacaoRedefinicaoSenha.tokenDoLink(it)) }
    }

    @Test fun rejeitaTokensDuplicadosMesmoQuandoOParametroEstaCodificado() {
        assertNull(ValidacaoRedefinicaoSenha.tokenDoLink("levit://reset-password?token=$token&token=$token"))
        assertNull(ValidacaoRedefinicaoSenha.tokenDoLink("levit://reset-password?token=$token&%74oken=$token"))
    }

    @Test fun validaFormatoExatoDoTokenAceitoPelaApi() {
        assertTrue(ValidacaoRedefinicaoSenha.tokenValido(token))
        assertFalse(ValidacaoRedefinicaoSenha.tokenValido("A".repeat(64)))
        assertFalse(ValidacaoRedefinicaoSenha.tokenValido("a".repeat(63)))
        assertFalse(ValidacaoRedefinicaoSenha.tokenValido(null))
    }
}
