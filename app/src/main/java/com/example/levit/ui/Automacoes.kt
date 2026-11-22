package com.example.levit.ui

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor

/*
 * Regras de automação e de notificação portadas do web
 * (utils/automacaoConstants.js e utils/notificacoes.js), sem Android.
 */

data class InfoComIcone(val rotulo: String, val icone: String)

/** Uma ação: tipo + configuração (o JSON que a API guarda). */
data class AcaoAutomacao(var tipo: String, var configuracao: MutableMap<String, Any?>)

/** Uma condição: coluna, operador e valor (e o "de" do "mudou de… para…"). */
data class RegraAutomacao(var campo: String = "", var operador: String = "igual", var valor: String = "", var valorDe: String = "")

data class CondicoesAutomacao(val logica: String, val regras: List<RegraAutomacao>)

object Automacoes {

    val GATILHOS = linkedMapOf(
        "criacao" to InfoComIcone("Novo item", "add_circle"),
        "atualizacao" to InfoComIcone("Atualização", "edit"),
        "exclusao" to InfoComIcone("Exclusão", "delete"),
        "candidato_criado" to InfoComIcone("Novo candidato", "person_add"),
        "candidato_mudou_etapa" to InfoComIcone("Mudou de etapa", "swap_horiz"),
    )

    val ACOES = linkedMapOf(
        "enviar_email" to InfoComIcone("Enviar e-mail", "mail"),
        "webhook" to InfoComIcone("Avisar outro sistema", "hub"),
        "notificacao" to InfoComIcone("Notificação", "notifications"),
        "atualizar_campo" to InfoComIcone("Atualizar coluna", "edit_note"),
        "criar_registro" to InfoComIcone("Criar item", "library_add"),
    )

    /** Em vagas, condições e {{variáveis}} usam os campos do candidato. id, nome, tipo. */
    val CAMPOS_CANDIDATO = listOf(
        Triple("nome", "Nome", "texto"),
        Triple("email", "E-mail", "texto"),
        Triple("telefone", "Telefone", "texto"),
        Triple("mensagem", "Mensagem", "texto"),
        Triple("etapa", "Etapa", "etapa"),
        Triple("etapa_anterior", "Etapa anterior", "etapa"),
    )

    val OPERADORES = linkedMapOf(
        "igual" to "é igual a",
        "diferente" to "é diferente de",
        "contem" to "contém",
        "nao_contem" to "não contém",
        "vazio" to "está vazio",
        "preenchido" to "está preenchido",
        "maior" to "é maior que",
        "menor" to "é menor que",
        "mudou" to "mudou (qualquer valor)",
        "mudou_para" to "mudou para",
        "mudou_de_para" to "mudou de… para…",
    )

    /** Comparam com o valor anterior: só disparam quando há um (atualização / troca de etapa). */
    val OPERADORES_DE_MUDANCA = listOf("mudou", "mudou_para", "mudou_de_para")

    /** Dispensam o campo "valor". */
    val OPERADORES_SEM_VALOR = listOf("vazio", "preenchido", "mudou")

    const val MAX_REGRAS = 10

    val DESTINO_NOTIFICACAO = linkedMapOf(
        "todos" to "Toda a equipe com acesso à tabela",
        "cargo" to "Pessoas de um cargo",
        "usuario" to "Uma pessoa específica",
        // Só aparece em tabelas com coluna "Responsável".
        "responsavel" to "O responsável pelo item",
    )

    val STATUS_EXECUCAO = mapOf(
        "sucesso" to InfoComIcone("Sucesso", "check_circle"),
        "erro" to InfoComIcone("Falhou", "error_outline"),
        "retentando" to InfoComIcone("Vai tentar de novo", "schedule"),
    )

    /** Tabelas de dados disparam com itens; vagas (recrutamento), com candidatos. */
    fun ehVaga(tipoDaTabela: String?) = tipoDaTabela == "recrutamento"

    fun gatilhosDoModulo(tipo: String?) =
        if (ehVaga(tipo)) listOf("candidato_criado", "candidato_mudou_etapa") else listOf("criacao", "atualizacao", "exclusao")

    /** Gatilho que fornece o "valor anterior" usado pelas condições de mudança. */
    fun gatilhoDeMudanca(tipo: String?) = if (ehVaga(tipo)) "candidato_mudou_etapa" else "atualizacao"

    /** Ações sobre itens não existem em vagas. */
    fun acoesDoModulo(tipo: String?) =
        if (ehVaga(tipo)) ACOES.keys.filter { it != "atualizar_campo" && it != "criar_registro" } else ACOES.keys.toList()

    fun gatilhoLabel(gatilho: String?) = GATILHOS[gatilho]?.rotulo ?: gatilho.orEmpty()
    fun acaoLabel(tipo: String?) = ACOES[tipo]?.rotulo ?: tipo.orEmpty()

    /** Configuração inicial de cada tipo de ação. */
    fun configuracaoInicial(tipo: String): MutableMap<String, Any?> = when (tipo) {
        "enviar_email" -> mutableMapOf("destinatario" to "", "assunto" to "", "corpo" to "")
        "webhook" -> mutableMapOf("url" to "")
        "notificacao" -> mutableMapOf("destinatarios" to "todos", "destino_id" to "", "titulo" to "", "mensagem" to "")
        "atualizar_campo" -> mutableMapOf("campo_id" to "", "valor" to "")
        "criar_registro" -> mutableMapOf("modulo_destino_id" to "", "mapeamento" to mutableMapOf<String, Any?>())
        else -> mutableMapOf()
    }

    fun novaAcao() = AcaoAutomacao("enviar_email", configuracaoInicial("enviar_email"))

    private fun texto(v: Any?): String = v?.toString().orEmpty()

    /** A ação tem o mínimo para ser salva? */
    fun acaoValida(a: AcaoAutomacao): Boolean {
        val c = a.configuracao
        return when (a.tipo) {
            "enviar_email" -> (texto(c["destinatario"]).isNotEmpty() || texto(c["destinatario_campo_id"]).isNotEmpty()) &&
                texto(c["assunto"]).isNotEmpty() && texto(c["corpo"]).isNotEmpty()
            "webhook" -> texto(c["url"]).isNotEmpty()
            "notificacao" -> texto(c["titulo"]).trim().isNotEmpty() &&
                (texto(c["destinatarios"]) in listOf("todos", "responsavel") || texto(c["destino_id"]).isNotEmpty())
            "atualizar_campo" -> texto(c["campo_id"]).isNotEmpty()
            // Ligar ao item que disparou já conta como coluna preenchida.
            "criar_registro" -> texto(c["modulo_destino_id"]).isNotEmpty() &&
                (texto(c["ligar_campo_id"]).isNotEmpty() || (c["mapeamento"] as? Map<*, *>).orEmpty().values.any { texto(it).trim().isNotEmpty() })
            else -> false
        }
    }

    /** A regra está completa para o operador escolhido? */
    fun regraValida(r: RegraAutomacao): Boolean {
        if (r.campo.isEmpty() || r.operador.isEmpty()) return false
        if (r.operador in OPERADORES_SEM_VALOR) return true
        if (r.operador == "mudou_de_para" && r.valorDe.isEmpty()) return false
        return r.valor.isNotEmpty()
    }

    /** Corpo enviado à API (criar, atualizar e simular) a partir do estado do formulário. */
    fun montarDefinicao(
        nome: String,
        gatilhos: List<String>,
        temCondicao: Boolean,
        logica: String,
        regras: List<RegraAutomacao>,
        acoes: List<AcaoAutomacao>
    ): Map<String, Any?> {
        val limpas = regras.map { r ->
            linkedMapOf(
                "campo" to r.campo,
                "operador" to r.operador,
                "valor" to if (r.operador in OPERADORES_SEM_VALOR) null else r.valor,
                "valor_de" to if (r.operador == "mudou_de_para") r.valorDe else null,
            )
        }
        return linkedMapOf(
            "nome" to nome.trim(),
            "gatilhos" to gatilhos,
            "condicoes" to if (temCondicao && limpas.isNotEmpty()) linkedMapOf("logica" to logica.ifEmpty { "e" }, "regras" to limpas) else null,
            "acoes" to acoes.map { linkedMapOf("tipo" to it.tipo, "configuracao" to it.configuracao) },
        )
    }

    private fun JsonObject.texto(chave: String): String? = get(chave)?.takeIf { it.isJsonPrimitive }?.asString

    /** Lista de gatilhos; linhas antigas só trazem `gatilho`. */
    fun gatilhosDaAutomacao(a: JsonObject?): List<String> {
        val lista = a?.get("gatilhos")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
        if (!lista.isNullOrEmpty()) return lista
        return listOfNotNull(a?.texto("gatilho")?.takeIf { it.isNotEmpty() })
    }

    /** Condições salvas (formato novo) ou a condição única do formato antigo. */
    fun condicoesDaAutomacao(a: JsonObject?): CondicoesAutomacao? {
        val nova = a?.get("condicoes")?.takeIf { it.isJsonObject }?.asJsonObject
        val regras = nova?.get("regras")?.takeIf { it.isJsonArray }?.asJsonArray
        if (regras != null && regras.size() > 0) {
            return CondicoesAutomacao(nova.texto("logica") ?: "e", regras.mapNotNull { e ->
                e.takeIf { it.isJsonObject }?.asJsonObject?.let { r ->
                    RegraAutomacao(r.texto("campo").orEmpty(), r.texto("operador") ?: "igual", r.texto("valor").orEmpty(), r.texto("valor_de").orEmpty())
                }
            })
        }
        val campoAntigo = a?.texto("campo_condicao_id")?.takeIf { it.isNotEmpty() } ?: return null
        return CondicoesAutomacao("e", listOf(RegraAutomacao(
            campoAntigo,
            a.texto("condicao_operador")?.takeIf { it.isNotEmpty() } ?: "igual",
            a.texto("condicao_valor").orEmpty(),
            a.texto("condicao_valor_de").orEmpty(),
        )))
    }

    /** "agora", "há 5 min", "há 3 h" ou a data/hora completa (mais de um dia). */
    fun quando(valor: String?, agora: Long = System.currentTimeMillis()): String {
        val data = Datas.ler(valor) ?: return "—"
        val minutos = floor((agora - data.time) / 60000.0).toLong()
        return when {
            minutos < 1 -> "agora"
            minutos < 60 -> "há $minutos min"
            minutos < 24 * 60 -> "há ${minutos / 60} h"
            else -> dataHoraCompleta(valor)
        }
    }

    /** dd/MM/aaaa HH:mm (dateStyle short, timeStyle short em pt-BR). */
    fun dataHoraCompleta(valor: String?): String =
        Datas.ler(valor)?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(it) }.orEmpty()

    fun duracao(ms: Long?): String = when {
        ms == null -> ""
        ms < 1000 -> "$ms ms"
        else -> String.format(Locale.US, "%.1f", ms / 1000.0).replace('.', ',') + " s"
    }

    /** Rótulo curto de um item: o primeiro valor preenchido. */
    fun rotuloRegistro(id: String?, dados: JsonObject?): String {
        val valor = dados?.entrySet()?.map { it.value }?.firstOrNull { e: JsonElement ->
            e.isJsonPrimitive && e.asString.isNotEmpty()
        }
        return valor?.asString?.take(60) ?: "Registro ${id.orEmpty().take(8)}"
    }
}

/** utils/notificacoes.js. */
object Notificacoes {
    /** Ícone e cores (texto, fundo) por tipo de notificação. */
    fun icone(tipo: String?): Triple<String, String, String> = when (tipo) {
        "candidatura" -> Triple("person_add", "primary_700", "primary_100")
        "automacao" -> Triple("bolt", "info", "info_bg")
        "automacao_pausada" -> Triple("pause_circle", "warning", "warning_bg")
        "convite_aceito" -> Triple("group_add", "success", "success_bg")
        else -> Triple("notifications", "light_text", "background")
    }

    private fun inicioDoDia(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Agrupa (mantendo a ordem) em "Hoje", "Ontem" e "12 de outubro". */
    fun <T> agruparPorDia(itens: List<T>, criadoEm: (T) -> String?, agora: Long = System.currentTimeMillis()): List<Pair<String, List<T>>> {
        val hoje = inicioDoDia(agora)
        val ontem = Calendar.getInstance().apply { timeInMillis = hoje; add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        val grupos = mutableListOf<Pair<String, MutableList<T>>>()
        itens.forEach { item ->
            val data = Datas.ler(criadoEm(item))
            val dia = data?.let { inicioDoDia(it.time) }
            val rotulo = when (dia) {
                hoje -> "Hoje"
                ontem -> "Ontem"
                null -> "Sem data"
                else -> SimpleDateFormat("dd 'de' MMMM", Locale.forLanguageTag("pt-BR")).format(Date(dia))
            }
            if (grupos.lastOrNull()?.first == rotulo) grupos.last().second += item else grupos += rotulo to mutableListOf(item)
        }
        return grupos
    }

    /** Rótulo do sino: "Notificações, 3 não lidas". */
    fun rotuloDoSino(naoLidas: Int) =
        if (naoLidas > 0) "Notificações, $naoLidas não ${if (naoLidas == 1) "lida" else "lidas"}" else "Notificações"

    fun contador(naoLidas: Int) = if (naoLidas > 99) "99+" else naoLidas.toString()
}
