package com.example.levit

import com.example.levit.data.remote.dto.Candidato
import com.example.levit.data.remote.dto.Comparacao
import com.example.levit.ui.Datas
import com.example.levit.ui.Funil
import com.example.levit.ui.Pessoas
import com.example.levit.ui.Relatorios
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RegrasTest {

    private fun candidato(id: String) = Candidato(id, "v", "Ana $id", null, null, null, null, null, null, null, false, null)

    @Test fun corDaEtapaIgualAoCoresDaFaseDoWeb() {
        // 5 etapas: 1ª clara, intermediárias espalhadas, última verde.
        assertEquals(listOf(0, 0, 1, 2, Funil.FINAL), (0 until 5).map { Funil.cor(it, 5) })
        assertEquals(listOf(0, Funil.FINAL), (0 until 2).map { Funil.cor(it, 2) })
        assertEquals(0, Funil.cor(0, 1))
    }

    @Test fun quadroVazioDoPhpViraListaVazia() {
        assertTrue(Funil.etapas(JsonParser.parseString("[]")).isEmpty())
        val etapas = Funil.etapas(JsonParser.parseString(
            """{"f1":{"fase":"Triagem","total":1,"candidatos":[{"id":"c1","nome":"Ana"}]},"f2":{"fase":"Entrevista","total":0,"candidatos":[]}}"""
        ))
        assertEquals(listOf("f1", "f2"), etapas.map { it.id })
        assertEquals("Triagem", etapas[0].nome)
        assertEquals("c1", etapas[0].candidatos.single().id)
    }

    @Test fun moverTiraDaEtapaDeOrigemEPoeNoFimDaDestino() {
        val etapas = Funil.etapas(JsonParser.parseString(
            """{"a":{"fase":"A","candidatos":[{"id":"1"},{"id":"2"}]},"b":{"fase":"B","candidatos":[{"id":"3"}]}}"""
        ))
        val depois = Funil.mover(etapas, etapas[0].candidatos[0], "a", "b", "2026-10-27 10:00:00")
        assertEquals(listOf("2"), depois[0].candidatos.map { it.id })
        assertEquals(listOf("3", "1"), depois[1].candidatos.map { it.id })
        assertEquals("b", depois[1].candidatos.last().faseAtualId)
    }

    @Test fun buscaIgnoraAcentoEMaiuscula() {
        assertEquals("joao conceicao", Funil.normalizar("JOÃO Conceição"))
        assertNotNull(candidato("x"))
    }

    @Test fun tempoRelativoComoNoWeb() {
        val agora = Datas.ler("2026-10-27 12:00:00")!!.time
        assertEquals("hoje", Datas.tempoRelativo("2026-10-27 08:00:00", agora))
        assertEquals("ontem", Datas.tempoRelativo("2026-10-26 08:00:00", agora))
        assertEquals("há 5 dias", Datas.tempoRelativo("2026-10-22 12:00:00", agora))
        assertEquals("há 2 meses", Datas.tempoRelativo("2026-08-20 12:00:00", agora))
        assertNull(Datas.tempoRelativo("2026-10-28 12:00:00", agora))
        assertNull(Datas.tempoRelativo("lixo", agora))
        assertTrue(Datas.estaParado("2026-10-13 12:00:00", agora = agora))
        assertFalse(Datas.estaParado("2026-10-14 12:00:01", agora = agora))
    }

    @Test fun datasDoPostgresComFracaoEFuso() {
        assertNotNull(Datas.ler("2026-10-20 12:34:56.123456+00"))
        assertNotNull(Datas.ler("2026-10-20T12:34:56Z"))
        assertNotNull(Datas.ler("2026-10-20"))
        assertNull(Datas.ler(null))
    }

    @Test fun corDaPessoaEstavelEIgualAoHashDoWeb() {
        // Em JS: 'ana' -> ((97*31+110)*31+97) >>> 0 = 96724 -> 96724 % 6 = 4
        assertEquals(4, Pessoas.indiceDaCor("Ana"))
        assertEquals(Pessoas.indiceDaCor("  ana "), Pessoas.indiceDaCor("ANA"))
        assertEquals(0, Pessoas.indiceDaCor(""))
        // Nome longo estoura 32 bits: o >>> 0 do JS vira o "and 0xFFFFFFFF".
        assertEquals(1, Pessoas.indiceDaCor("Maria Aparecida dos Santos Oliveira"))
    }

    @Test fun iniciaisEMascaraDeTelefone() {
        assertEquals("AS", Pessoas.iniciais("Ana Clara Souza"))
        assertEquals("?", Pessoas.iniciais("  "))
        assertEquals("(11) 91234-5678", Pessoas.mascararTelefone("11912345678"))
        assertEquals("(11) 1234-5678", Pessoas.mascararTelefone("1112345678"))
        assertEquals("(11", Pessoas.mascararTelefone("11"))
    }

    @Test fun validacaoDoCandidatoIgualAoWeb() {
        val erros = Pessoas.errosDoCandidato("Al", "x@y", "1199")
        assertEquals(setOf("nome", "email", "telefone"), erros.keys)
        assertTrue(Pessoas.errosDoCandidato("Ana Souza", "ana@empresa.com", "").isEmpty())
    }

    @Test fun periodosDosAtalhos() {
        assertEquals("2026-10-21" to "2026-10-27", Relatorios.periodoDoAtalho("7d", "2026-10-27"))
        assertEquals("2026-09-28" to "2026-10-27", Relatorios.periodoDoAtalho("30d", "2026-10-27"))
        assertEquals("2026-10-01" to "2026-10-27", Relatorios.periodoDoAtalho("mes", "2026-10-27"))
        assertEquals("2026-09-01" to "2026-09-30", Relatorios.periodoDoAtalho("mes-passado", "2026-10-27"))
        assertEquals("2026-02-01" to "2026-02-28", Relatorios.periodoDoAtalho("mes-passado", "2026-03-15"))
        assertNull(Relatorios.periodoDoAtalho("outro", "2026-10-27"))
        assertEquals("01/10 a 07/10/2026", Relatorios.textoDoPeriodo("2026-10-01", "2026-10-07"))
        assertEquals("07/10/2026", Relatorios.textoDoPeriodo("2026-10-07", "2026-10-07"))
    }

    @Test fun variacaoComoODescreverVariacaoDoWeb() {
        assertEquals("+12%", Relatorios.variacao(Comparacao(112.0, 100.0, 12.0))!!.texto)
        assertEquals("−2,5%", Relatorios.variacao(Comparacao(1.0, 1.0, -2.5))!!.texto)
        assertEquals("novo", Relatorios.variacao(Comparacao(3.0, 0.0, null))!!.texto)
        assertNull(Relatorios.variacao(Comparacao(0.0, 0.0, null)))
        assertEquals("igual", Relatorios.variacao(Comparacao(1.0, 1.0, 0.0))!!.texto)
        // Saídas subindo é ruim.
        assertEquals(false, Relatorios.variacao(Comparacao(2.0, 1.0, 100.0), subirEBom = false)!!.bom)
        assertEquals("R$ 1.234,50", Relatorios.moeda(1234.5))
    }

    @Test fun csvComoOGerarCsvDoWeb() {
        val csv = Relatorios.gerarCsv(listOf("Cliente", "Itens"), listOf(
            listOf("=HYPERLINK(\"x\")", 3.0), listOf("-12", 1.5), listOf("Ana; Bia", null)
        ))
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals(
            listOf("Cliente;Itens", "\"'=HYPERLINK(\"\"x\"\")\";3", "-12;1.5", "\"Ana; Bia\";"),
            csv.removePrefix("\uFEFF").split("\r\n")
        )
    }

    @Test fun prazoComoOSituacaoDoPrazoDoWeb() {
        val hoje = "2026-10-27"
        assertEquals(com.example.levit.ui.Prazos.Tipo.ATRASADO, com.example.levit.ui.Prazos.situacao("2026-10-20", hoje)!!.tipo)
        assertEquals("Atrasado há 7 dias", com.example.levit.ui.Prazos.situacao("2026-10-20", hoje)!!.rotulo)
        assertEquals("Atrasado desde ontem", com.example.levit.ui.Prazos.situacao("2026-10-26", hoje)!!.rotulo)
        assertEquals("Vence hoje", com.example.levit.ui.Prazos.situacao("2026-10-27", hoje)!!.rotulo)
        assertEquals("Vence amanhã", com.example.levit.ui.Prazos.situacao("2026-10-28", hoje)!!.rotulo)
        assertEquals("Vence em 7 dias", com.example.levit.ui.Prazos.situacao("2026-11-03", hoje)!!.rotulo)
        assertEquals(com.example.levit.ui.Prazos.Tipo.FUTURO, com.example.levit.ui.Prazos.situacao("2026-12-25", hoje)!!.tipo)
        assertEquals(com.example.levit.ui.Prazos.Tipo.CONCLUIDO, com.example.levit.ui.Prazos.situacao("2026-10-20", hoje, concluido = true)!!.tipo)
        assertNull(com.example.levit.ui.Prazos.situacao("27/10/2026", hoje))
    }

    @Test fun conclusaoIgnoraAcentoEMaiuscula() {
        assertTrue(com.example.levit.ui.Prazos.itemConcluido(listOf("A fazer", "Concluído")))
        assertTrue(com.example.levit.ui.Prazos.opcaoDeConclusao("  ENTREGUE "))
        assertFalse(com.example.levit.ui.Prazos.itemConcluido(listOf("Em andamento", null)))
        assertEquals("Todo mês", com.example.levit.ui.Prazos.rotuloDaRepeticao("mensal"))
    }

    @Test fun rotuloDosItensLigados() {
        assertEquals("2 itens ligados", com.example.levit.ui.Vinculos.rotulo(listOf("a", "b"), null))
        assertEquals("Ana, Item sem nome, Item excluído",
            com.example.levit.ui.Vinculos.rotulo(listOf("a", "b", "c"), mapOf("a" to "Ana", "b" to "")))
        assertNull(com.example.levit.ui.Vinculos.rotulo(emptyList(), emptyMap()))
    }
}
