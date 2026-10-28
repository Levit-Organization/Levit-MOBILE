package com.example.levit

import com.example.levit.ui.AcaoAutomacao
import com.example.levit.ui.Automacoes
import com.example.levit.ui.Notificacoes
import com.example.levit.ui.RegraAutomacao
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/** Os mesmos casos de automacaoConstants.test.js e notificacoes.test.js do web. */
class AutomacoesTest {

    private fun json(s: String): JsonObject = JsonParser.parseString(s).asJsonObject

    private fun em(ano: Int, mes: Int, dia: Int, hora: Int, min: Int = 0) = Calendar.getInstance().apply {
        clear(); set(ano, mes - 1, dia, hora, min, 0)
    }.timeInMillis

    // --- quando / duracao -------------------------------------------------------------

    @Test
    fun quandoRelativoEDataCompleta() {
        val agora = em(2026, 10, 10, 12)
        assertEquals("agora", Automacoes.quando("2026-10-10 11:59:40", agora))
        assertEquals("há 5 min", Automacoes.quando("2026-10-10 11:55:00", agora))
        assertEquals("há 3 h", Automacoes.quando("2026-10-10 09:00:00", agora))
        assertTrue(Automacoes.quando("2026-10-01 08:30:00", agora).contains("01/10/2026"))
        assertEquals("—", Automacoes.quando(null, agora))
        assertEquals("—", Automacoes.quando("xyz", agora))
    }

    @Test
    fun duracaoEmMsOuSegundos() {
        assertEquals("320 ms", Automacoes.duracao(320))
        assertEquals("1,5 s", Automacoes.duracao(1530))
        assertEquals("", Automacoes.duracao(null))
    }

    // --- gatilhos / condições salvas ----------------------------------------------------

    @Test
    fun gatilhosUsaListaNovaECaiNoAntigo() {
        assertEquals(listOf("criacao", "exclusao"), Automacoes.gatilhosDaAutomacao(json("""{"gatilhos":["criacao","exclusao"],"gatilho":"criacao"}""")))
        assertEquals(listOf("atualizacao"), Automacoes.gatilhosDaAutomacao(json("""{"gatilho":"atualizacao"}""")))
        assertEquals(emptyList<String>(), Automacoes.gatilhosDaAutomacao(json("{}")))
    }

    @Test
    fun condicoesPrefereFormatoNovoEConverteAntigo() {
        val nova = Automacoes.condicoesDaAutomacao(json("""{"condicoes":{"logica":"ou","regras":[{"campo":"x","operador":"vazio"}]},"campo_condicao_id":"c1"}"""))!!
        assertEquals("ou", nova.logica)
        assertEquals(listOf(RegraAutomacao("x", "vazio", "", "")), nova.regras)

        val antiga = Automacoes.condicoesDaAutomacao(json("""{"campo_condicao_id":"c1","condicao_operador":"mudou_para","condicao_valor":"Fechado"}"""))!!
        assertEquals("e", antiga.logica)
        assertEquals(listOf(RegraAutomacao("c1", "mudou_para", "Fechado", "")), antiga.regras)

        assertNull(Automacoes.condicoesDaAutomacao(json("{}")))
    }

    // --- montarDefinicao -------------------------------------------------------------------

    private val regras = listOf(
        RegraAutomacao("c1", "igual", "Sim", "lixo"),
        RegraAutomacao("c2", "preenchido", "ignorado", ""),
        RegraAutomacao("c3", "mudou_de_para", "B", "A"),
    )
    private val acoes = listOf(AcaoAutomacao("webhook", mutableMapOf("url" to "https://x.com")))

    @Test
    fun montarDefinicaoComCondicoesLimpaValoresSemUso() {
        val d = Automacoes.montarDefinicao("  Avisar RH ", listOf("criacao", "atualizacao"), true, "ou", regras, acoes)
        assertEquals(mapOf(
            "nome" to "Avisar RH",
            "gatilhos" to listOf("criacao", "atualizacao"),
            "condicoes" to mapOf(
                "logica" to "ou",
                "regras" to listOf(
                    mapOf("campo" to "c1", "operador" to "igual", "valor" to "Sim", "valor_de" to null),
                    mapOf("campo" to "c2", "operador" to "preenchido", "valor" to null, "valor_de" to null),
                    mapOf("campo" to "c3", "operador" to "mudou_de_para", "valor" to "B", "valor_de" to "A"),
                ),
            ),
            "acoes" to listOf(mapOf("tipo" to "webhook", "configuracao" to mapOf("url" to "https://x.com"))),
        ), d)
    }

    @Test
    fun montarDefinicaoSemCondicoesEnviaNull() {
        assertNull(Automacoes.montarDefinicao("A", listOf("criacao"), false, "ou", regras, acoes)["condicoes"])
        assertNull(Automacoes.montarDefinicao("A", listOf("criacao"), true, "ou", emptyList(), acoes)["condicoes"])
        assertTrue(Automacoes.montarDefinicao("A", listOf("criacao"), false, "ou", regras, acoes).containsKey("condicoes"))
    }

    // --- regraValida ----------------------------------------------------------------------

    @Test
    fun regraValidaPorOperador() {
        for (op in listOf("mudou", "vazio", "preenchido")) assertTrue(op, Automacoes.regraValida(RegraAutomacao("c1", op)))
        assertFalse(Automacoes.regraValida(RegraAutomacao("c1", "igual")))
        assertTrue(Automacoes.regraValida(RegraAutomacao("c1", "contem", "x")))
        assertFalse(Automacoes.regraValida(RegraAutomacao("", "vazio")))
        assertFalse(Automacoes.regraValida(RegraAutomacao("c1", "mudou_de_para", "X")))
        assertTrue(Automacoes.regraValida(RegraAutomacao("c1", "mudou_de_para", "X", "Y")))
        assertTrue(Automacoes.regraValida(RegraAutomacao("c1", "maior", "0")))
    }

    // --- gatilhos e ações por tipo de tabela ---------------------------------------------

    @Test
    fun vagasUsamGatilhosDeCandidato() {
        assertEquals(listOf("candidato_criado", "candidato_mudou_etapa"), Automacoes.gatilhosDoModulo("recrutamento"))
        assertEquals("candidato_mudou_etapa", Automacoes.gatilhoDeMudanca("recrutamento"))
        assertFalse(Automacoes.acoesDoModulo("recrutamento").contains("atualizar_campo"))
        assertFalse(Automacoes.acoesDoModulo("recrutamento").contains("criar_registro"))
    }

    @Test
    fun dadosUsamGatilhosDeRegistroETodasAsAcoes() {
        assertEquals(listOf("criacao", "atualizacao", "exclusao"), Automacoes.gatilhosDoModulo("dados"))
        assertEquals("atualizacao", Automacoes.gatilhoDeMudanca("dados"))
        assertTrue(Automacoes.acoesDoModulo("dados").containsAll(listOf("atualizar_campo", "criar_registro", "notificacao")))
    }

    // --- acaoValida ------------------------------------------------------------------------

    private fun acao(tipo: String, vararg config: Pair<String, Any?>) = AcaoAutomacao(tipo, mutableMapOf(*config))

    @Test
    fun acaoValidaSobreDados() {
        assertTrue(Automacoes.acaoValida(acao("atualizar_campo", "campo_id" to "c1", "valor" to "")))
        assertFalse(Automacoes.acaoValida(acao("atualizar_campo", "campo_id" to "")))
        assertFalse(Automacoes.acaoValida(acao("criar_registro", "modulo_destino_id" to "m2", "mapeamento" to mapOf("a" to "  "))))
        assertTrue(Automacoes.acaoValida(acao("criar_registro", "modulo_destino_id" to "m2", "mapeamento" to mapOf("a" to "{{Nome}}"))))
        assertTrue(Automacoes.acaoValida(acao("criar_registro", "modulo_destino_id" to "m2", "mapeamento" to emptyMap<String, Any>(), "ligar_campo_id" to "c-lead")))
    }

    @Test
    fun acaoValidaNotificacao() {
        assertTrue(Automacoes.acaoValida(acao("notificacao", "destinatarios" to "todos", "titulo" to "Oi")))
        assertFalse(Automacoes.acaoValida(acao("notificacao", "destinatarios" to "todos", "titulo" to "  ")))
        assertFalse(Automacoes.acaoValida(acao("notificacao", "destinatarios" to "cargo", "titulo" to "Oi", "destino_id" to "")))
        assertTrue(Automacoes.acaoValida(acao("notificacao", "destinatarios" to "cargo", "titulo" to "Oi", "destino_id" to "k")))
        assertTrue(Automacoes.acaoValida(acao("notificacao", "destinatarios" to "responsavel", "titulo" to "Vence hoje")))
    }

    // --- rotuloRegistro ----------------------------------------------------------------------

    @Test
    fun rotuloRegistroUsaPrimeiroValorPreenchido() {
        assertEquals("Ana Souza", Automacoes.rotuloRegistro("abc", json("""{"a":"","b":["x"],"c":"Ana Souza"}""")))
        assertEquals("Registro 01234567", Automacoes.rotuloRegistro("0123456789", json("{}")))
    }

    // --- Notificações --------------------------------------------------------------------------

    @Test
    fun agruparPorDiaEmHojeOntemEData() {
        val agora = em(2026, 10, 10, 15)
        val itens = listOf(
            1 to "2026-10-10 14:00:00",
            2 to "2026-10-10 08:00:00",
            3 to "2026-10-09 22:00:00",
            4 to "2026-10-01 10:00:00",
        )
        val grupos = Notificacoes.agruparPorDia(itens, { it.second }, agora)
        assertEquals(listOf("Hoje", "Ontem", "01 de outubro"), grupos.map { it.first })
        assertEquals(listOf(1, 2), grupos[0].second.map { it.first })
        assertEquals(emptyList<Pair<String, List<Int>>>(), Notificacoes.agruparPorDia(emptyList<Int>(), { null }, agora))
    }

    @Test
    fun contadorDoSino() {
        assertEquals("99+", Notificacoes.contador(120))
        assertEquals("Notificações, 1 não lida", Notificacoes.rotuloDoSino(1))
        assertEquals("Notificações", Notificacoes.rotuloDoSino(0))
    }
}
