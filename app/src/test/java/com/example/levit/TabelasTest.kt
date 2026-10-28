package com.example.levit

import com.example.levit.data.remote.dto.Coluna
import com.example.levit.data.remote.dto.LigacaoRecebida
import com.example.levit.data.remote.dto.TabelaDetalhe
import com.example.levit.data.remote.dto.TabelaResumo
import com.example.levit.ui.Filtros
import com.example.levit.ui.Importacao
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Os mesmos casos de importacao.test.js e filtros.test.js do web. */
class TabelasTest {

    private fun col(id: String, nome: String, tipo: String, opcoes: String? = null, alvo: String? = null, tabela: TabelaResumo? = null) =
        Coluna(id, nome, tipo, opcoes?.let { JsonParser.parseString(it) }, alvo, tabela)

    // --- Importação -------------------------------------------------------------------

    @Test fun leOCsvDoExcelBrasileiro() {
        val csv = "Cliente;Valor;Obs\r\nPadaria;\"1.234,56\";\"tem \"\"aspas\"\"\"\r\n\"Oficina; do Zé\";10;\"duas\nlinhas\"\r\n\r\n"
        assertEquals(listOf(
            listOf("Cliente", "Valor", "Obs"),
            listOf("Padaria", "1.234,56", "tem \"aspas\""),
            listOf("Oficina; do Zé", "10", "duas\nlinhas"),
        ), Importacao.lerCsv(csv))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Importacao.lerCsv("a,b\n1,2"))
    }

    @Test fun caiParaWindows1252() {
        assertEquals("Clínica", Importacao.decodificarTexto(byteArrayOf(0x43, 0x6c, 0xed.toByte(), 0x6e, 0x69, 0x63, 0x61)))
    }

    private val pessoas = listOf("u1" to "Ana Souza", "u2" to "Bruno Lima", "u3" to "Ana Paula")
    private fun converter(bruto: Any?, c: Coluna, ligados: Map<String, Map<String, String?>> = emptyMap()) =
        Importacao.converterCelula(bruto, c, pessoas, ligados)

    @Test fun converteCadaTipoComoOWeb() {
        val numero = col("n", "Valor", "numero")
        assertEquals(1234.56, converter("R$ 1.234,56", numero).first)
        assertEquals(1234.5, converter("1,234.5", numero).first)
        assertNotNull(converter("abc", numero).second)

        val prazo = col("p", "Prazo", "prazo")
        assertEquals("2026-10-20", converter("20/10/2026", prazo).first)
        assertEquals("2026-03-05", converter("5/3/26", col("d", "Data", "data")).first)
        assertEquals("2026-10-20", converter(Importacao.DataExcel("2026-10-20"), prazo).first)
        assertNotNull(converter("31/02/2026", prazo).second)

        assertEquals("Entregue", converter("entregue", col("s", "Status", "selecao", "[\"A fazer\",\"Entregue\"]")).first)
        assertTrue(converter("Pago", col("s", "Status", "selecao", "[\"A fazer\"]")).second!!.contains("Status"))

        val resp = col("r", "Resp", "responsavel")
        assertEquals("u2", converter("bruno", resp).first)
        assertEquals("u1", converter("Ana Souza", resp).first)
        assertTrue(converter("Ana", resp).second!!.contains("ambíguo"))

        val rep = col("x", "Repete", "recorrencia")
        assertEquals("mensal", converter("Mensal", rep).first)
        assertEquals("mensal", converter("Todo mês", rep).first)

        assertEquals(null to null, converter("  ", col("t", "T", "texto")))
    }

    @Test fun casaColunasESeparaLinhasComProblema() {
        val campos = listOf(col("c1", "Cliente", "texto"), col("c2", "Vencimento", "prazo"), col("c3", "Status", "selecao", "[\"A fazer\",\"Entregue\"]"))
        val mapa = Importacao.mapearColunas(listOf("status", "CLIENTE", "Vencimento", "Extra"), campos)
        assertEquals(mapOf("c1" to 1, "c2" to 2, "c3" to 0), mapa)
        val p = Importacao.prepararItens(
            listOf(listOf("a fazer", "Padaria", "20/10/2026", "x"), listOf("Pago", "Oficina", "21/10/2026", "y")),
            campos, mapa, emptyList(), emptyMap()
        )
        assertEquals(listOf(2 to mapOf("c1" to "Padaria", "c2" to "2026-10-20", "c3" to "A fazer")), p.itens)
        assertEquals(listOf(Importacao.Problema(3, "Status: \"Pago\" não é uma opção de Status")), p.problemas)
    }

    @Test fun ligacaoPeloNomeDoItem() {
        val ligados = mapOf("mod-c" to mapOf("id-ana" to "Ana Souza", "id-bia" to "Bia", "id-x1" to "Duplicado", "id-x2" to "duplicado"))
        val um = col("v", "Cliente", "vinculo", "{\"multiplo\":false}", "mod-c")
        val varios = col("v", "Cliente", "vinculo", "{\"multiplo\":true}", "mod-c")
        assertEquals("id-ana", converter("ana souza", um, ligados).first)
        assertEquals(listOf("id-bia"), converter("Bia", varios, ligados).first)
        assertEquals(listOf("id-ana", "id-bia"), converter("Ana Souza; Bia, bia", varios, ligados).first)
        assertTrue(converter("Ana Souza, Bia", um, ligados).second!!.contains("não está"))
        assertTrue(converter("Duplicado", um, ligados).second!!.contains("ambíguo"))
        assertTrue(converter("Carla", um, ligados).second!!.contains("não está"))
        assertTrue(converter("Ana", um, emptyMap()).second!!.contains("não foi possível"))
    }

    // --- Filtros ----------------------------------------------------------------------

    private val clientes = TabelaResumo("mod-c", "Clientes", null, true, listOf(col("c-nome", "Nome", "texto"), col("c-cidade", "Cidade", "texto")))
    private val pedidosCampos = listOf(
        col("p-num", "Número", "texto"),
        col("p-valor", "Valor", "numero"),
        col("p-cliente", "Cliente", "vinculo", alvo = "mod-c", tabela = clientes),
        col("p-oculto", "Segredo", "vinculo", alvo = "mod-x", tabela = TabelaResumo("mod-x", "X", null, false, emptyList())),
    )
    private val pedidos = TabelaDetalhe("mod-p", "Pedidos", null, "dados", pedidosCampos, emptyList())
    private val clientesComPedidos = TabelaDetalhe("mod-c", "Clientes", null, "dados", clientes.campos,
        listOf(LigacaoRecebida("p-cliente", "Cliente", TabelaResumo("mod-p", "Pedidos", null, true, pedidosCampos))))

    @Test fun alvosDaTabelaEDasLigadas() {
        val chaves = Filtros.alvos(pedidos).map { it.chave }
        assertEquals(listOf("p-num", "p-valor", "p-cliente", "p-oculto", "criado_em", "p-cliente>c-nome", "p-cliente>c-cidade"), chaves)
        assertEquals("Cliente › Cidade", Filtros.alvos(pedidos).first { it.chave == "p-cliente>c-cidade" }.rotulo)

        val alvos = Filtros.alvos(clientesComPedidos)
        val existencia = alvos.first { it.chave == "p-cliente<" }
        assertEquals("ligacao", existencia.campo.tipo)
        assertEquals(listOf(Filtros.Via("p-cliente", "volta")), existencia.via)
        assertTrue(alvos.any { it.chave == "p-cliente<p-valor" })
    }

    @Test fun converteParaAApiEIgnoraIncompletas() {
        val alvos = Filtros.alvos(clientesComPedidos)
        val json = Filtros.paraApi("ou", listOf(
            Filtros.Regra("c-cidade", "contem", " Recife "),
            Filtros.Regra("c-nome", "igual", ""),
            Filtros.Regra("p-cliente<p-valor", "entre", "10", "20"),
            Filtros.Regra("p-cliente<", "vazio"),
            Filtros.Regra("", ""),
        ), alvos)
        assertEquals(JsonParser.parseString("""
            {"logica":"ou","regras":[
              {"campo":"c-cidade","operador":"contem","valor":"Recife"},
              {"campo":"p-valor","operador":"entre","valor":"10","valor_ate":"20","via":[{"campo":"p-cliente","sentido":"volta"}]},
              {"campo":"","operador":"vazio","via":[{"campo":"p-cliente","sentido":"volta"}]}
            ]}"""), JsonParser.parseString(json))
        assertNull(Filtros.paraApi("e", listOf(Filtros.Regra("c-nome", "contem", "  ")), alvos))
        val nome = alvos.first { it.chave == "c-nome" }
        assertFalse(Filtros.completa(Filtros.Regra("c-nome", "maior", "3"), nome))
        assertTrue(Filtros.completa(Filtros.Regra("c-nome", "contem", "3"), nome))
    }

    @Test fun operadoresNaLinguaDeCadaTipo() {
        assertEquals("depois de", Filtros.operadoresDoTipo("data").first { it.first == "maior" }.second)
        assertEquals(listOf("tem algum item", "não tem nenhum item"), Filtros.operadoresDoTipo("ligacao").map { it.second })
    }
}
