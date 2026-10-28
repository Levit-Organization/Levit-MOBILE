package com.example.levit

import com.example.levit.auth.ValidacaoCadastro
import org.junit.Assert.*
import org.junit.Test

class ValidacaoCadastroTest {

    @Test fun mascaraIgualAoWebEnquantoDigita() {
        assertEquals("", ValidacaoCadastro.mascararCpfCnpj(""))
        assertEquals("123.4", ValidacaoCadastro.mascararCpfCnpj("1234"))
        assertEquals("529.982.247-25", ValidacaoCadastro.mascararCpfCnpj("52998224725"))
        assertEquals("11.444.777/0001-61", ValidacaoCadastro.mascararCpfCnpj("11444777000161"))
        // Colar com letras, pontuação ou dígitos a mais não quebra a máscara.
        assertEquals("11.444.777/0001-61", ValidacaoCadastro.mascararCpfCnpj("11.444.777/0001-61999abc"))
    }

    @Test fun aceitaCpfECnpjComDigitoVerificadorCerto() {
        assertTrue(ValidacaoCadastro.documentoValido("529.982.247-25"))
        assertTrue(ValidacaoCadastro.documentoValido("11.444.777/0001-61"))
    }

    @Test fun recusaDocumentoComTamanhoOuDigitoErrado() {
        listOf("", "123", "529.982.247-24", "111.111.111-11", "11.444.777/0001-62",
            "00.000.000/0000-00", "1234567890123").forEach {
            assertFalse(it, ValidacaoCadastro.documentoValido(it))
        }
    }

    @Test fun forcaDaSenhaIgualAoRegisterDoWeb() {
        assertEquals(0, ValidacaoCadastro.forca(""))
        assertEquals(1, ValidacaoCadastro.forca("abcdefgh"))
        assertEquals(2, ValidacaoCadastro.forca("abcdefg1"))
        assertEquals(3, ValidacaoCadastro.forca("Abcdefg1"))
        assertEquals(4, ValidacaoCadastro.forca("Abcdef1!"))
    }

    @Test fun senhaSegueARegraDoBackend() {
        assertTrue(ValidacaoCadastro.senhaValida("abcde123"))
        assertFalse(ValidacaoCadastro.senhaValida("abcdefgh"))
        assertFalse(ValidacaoCadastro.senhaValida("Ab1!"))
    }
}
