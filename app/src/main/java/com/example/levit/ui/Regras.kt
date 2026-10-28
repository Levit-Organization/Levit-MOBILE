package com.example.levit.ui

import com.example.levit.data.remote.dto.Candidato
import com.example.levit.data.remote.dto.ColunaKanban
import com.example.levit.data.remote.dto.Comparacao
import com.example.levit.data.remote.dto.Etapa
import com.google.gson.Gson
import com.google.gson.JsonElement
import java.text.Normalizer
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Regras de apresentação portadas do web, sem dependência de Android para
 * poderem ser testadas na JVM. Cada função diz de onde veio.
 */

private val PT_BR = Locale.forLanguageTag("pt-BR")

/** Datas do backend: "AAAA-MM-DD HH:MM:SS" (às vezes com fração ou fuso no fim). */
object Datas {
    private const val DIA_MS = 86_400_000L

    /**
     * Data de candidato e de colaborador: o web faz new Date(valor) sem fuso,
     * que o navegador lê no horário local. Relatórios gravam em UTC (o web
     * acrescenta "Z"): para eles, [utc] = true.
     */
    fun ler(valor: String?, utc: Boolean = false): Date? {
        val texto = valor?.trim()?.replace('T', ' ') ?: return null
        val formato = when {
            Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*").matches(texto) -> "yyyy-MM-dd HH:mm:ss" to texto.take(19)
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(texto) -> "yyyy-MM-dd" to texto
            else -> return null
        }
        return runCatching {
            SimpleDateFormat(formato.first, Locale.US).apply {
                isLenient = false
                if (utc) timeZone = TimeZone.getTimeZone("UTC")
            }.parse(formato.second)
        }.getOrNull()
    }

    /** tempoRelativo de components/ui/funil.js: "hoje", "ontem", "há 3 dias", "há 2 meses"... */
    fun tempoRelativo(valor: String?, agora: Long = System.currentTimeMillis()): String? {
        val data = ler(valor) ?: return null
        val dias = floor((agora - data.time).toDouble() / DIA_MS).toLong()
        if (dias < 0) return null
        if (dias == 0L) return "hoje"
        if (dias == 1L) return "ontem"
        if (dias < 30) return "há $dias dias"
        val meses = dias / 30
        if (meses == 1L) return "há 1 mês"
        if (meses < 12) return "há $meses meses"
        val anos = meses / 12
        return if (anos == 1L) "há 1 ano" else "há $anos anos"
    }

    /** estaParado: 14 dias ou mais na mesma etapa. */
    fun estaParado(valor: String?, limiteDias: Int = 14, agora: Long = System.currentTimeMillis()): Boolean {
        val data = ler(valor) ?: return false
        return floor((agora - data.time).toDouble() / DIA_MS) >= limiteDias
    }

    /** Dias até a data (arredondando para cima), como diasAte de TeamManagement.jsx. */
    fun diasAte(valor: String?, agora: Long = System.currentTimeMillis()): Long? {
        val data = ler(valor) ?: return null
        return kotlin.math.ceil((data.time - agora).toDouble() / DIA_MS).toLong()
    }

    /** "12 de out. de 2026" (Intl pt-BR, day 2-digit, month short). */
    fun desde(valor: String?): String? = ler(valor)?.let {
        SimpleDateFormat("dd 'de' MMM 'de' yyyy", PT_BR).format(it)
    }

    /** "dd/MM/aaaa HH:mm" no horário local, para datas gravadas em UTC. */
    fun dataHora(utc: String?): String = ler(utc, utc = true)?.let {
        SimpleDateFormat("dd/MM/yyyy HH:mm", PT_BR).format(it)
    }.orEmpty()

    /** "dd/MM/aaaa" de uma data AAAA-MM-DD. */
    fun dataCurta(iso: String?): String = iso?.take(10)?.let { ler(it) }?.let {
        SimpleDateFormat("dd/MM/yyyy", PT_BR).format(it)
    }.orEmpty()

    /** Agora no mesmo formato do backend, no horário local (como o web lê). */
    fun agora(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    /** hojeISO de utils/prazos.js: AAAA-MM-DD no horário local. */
    fun iso(data: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(data)

    fun somarDias(iso: String, dias: Int): String {
        val cal = Calendar.getInstance().apply { time = ler(iso)!!; add(Calendar.DAY_OF_MONTH, dias) }
        return iso(cal.time)
    }
}

/** Cor do quadro por POSIÇÃO da etapa (coresDaFase de components/ui/funil.js). 0..3 = stage_1..4, 4 = final (verde). */
object Funil {
    const val FINAL = 4

    fun cor(indice: Int, total: Int): Int {
        if (total > 1 && indice == total - 1) return FINAL
        if (indice <= 0) return 0
        val passos = max(total - 2, 1)
        // Math.round do JS arredonda .5 para cima; roundToInt também.
        return min(((indice - 1).toDouble() / passos * 3).roundToInt(), 3)
    }

    /**
     * O quadro chega como { faseId: { fase, total, candidatos } } ou [] quando
     * não há etapas. Vira lista na ordem em que a API mandou.
     */
    fun etapas(bruto: JsonElement?, gson: Gson = Gson()): List<Etapa> {
        if (bruto == null || !bruto.isJsonObject) return emptyList()
        return bruto.asJsonObject.entrySet().map { (id, valor) ->
            val coluna = gson.fromJson(valor, ColunaKanban::class.java)
            Etapa(id, coluna.fase ?: id, coluna.candidatos.orEmpty())
        }
    }

    /** Move um candidato entre etapas sem buscar de novo (atualização otimista, como o web). */
    fun mover(etapas: List<Etapa>, candidato: Candidato, de: String, para: String, agoraIso: String): List<Etapa> {
        val movido = candidato.copy(faseAtualId = para, atualizadoEm = agoraIso)
        return etapas.map { etapa ->
            when (etapa.id) {
                de -> etapa.copy(candidatos = etapa.candidatos.filterNot { it.id == candidato.id })
                para -> etapa.copy(candidatos = etapa.candidatos.filterNot { it.id == candidato.id } + movido)
                else -> etapa
            }
        }
    }

    /** Busca por nome sem ligar para acento nem maiúscula (normalizar de RecrutamentoKanban.jsx). */
    fun normalizar(texto: String?): String =
        Normalizer.normalize(texto.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase(PT_BR)
}

/** Avatar.jsx: mesma pessoa, mesma cor, em qualquer tela (e no web). */
object Pessoas {
    const val TOTAL_CORES = 6

    fun indiceDaCor(nome: String?): Int {
        val chave = nome.orEmpty().trim().lowercase()
        if (chave.isEmpty()) return 0
        var hash = 0L
        for (c in chave) hash = (hash * 31 + c.code) and 0xFFFFFFFFL
        return (hash % TOTAL_CORES).toInt()
    }

    /** initialsFrom de Avatar.jsx. */
    fun iniciais(nome: String?): String {
        val partes = nome.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (partes.isEmpty()) return "?"
        val primeira = partes.first().take(1)
        val ultima = if (partes.size > 1) partes.last().take(1) else ""
        return (primeira + ultima).uppercase()
    }

    /** mascararTelefone de utils/masks.js: (11) 91234-5678 ou (11) 1234-5678. */
    fun mascararTelefone(valor: String): String {
        val d = valor.filter { it in '0'..'9' }.take(11)
        return when {
            d.isEmpty() -> ""
            d.length <= 2 -> "(${d}"
            d.length <= 6 -> "(${d.take(2)}) ${d.drop(2)}"
            d.length <= 10 -> "(${d.take(2)}) ${d.substring(2, 6)}-${d.drop(6)}"
            else -> "(${d.take(2)}) ${d.substring(2, 7)}-${d.drop(7)}"
        }
    }

    /** validarCandidato de utils/candidato.js (sem o arquivo, que a tela confere). */
    fun errosDoCandidato(nome: String, email: String, telefone: String): Map<String, String> {
        val erros = mutableMapOf<String, String>()
        if (nome.trim().length < 3) erros["nome"] = "Escreva o nome completo."
        if (email.isBlank()) erros["email"] = "Informe o e-mail."
        else if (!Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())) erros["email"] = "Esse e-mail não parece válido."
        if (telefone.isNotEmpty() && telefone.count { it in '0'..'9' } < 10) erros["telefone"] = "Telefone incompleto."
        return erros
    }

    fun emailValido(email: String) = Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim())
}

/** utils/relatorios.js. */
object Relatorios {

    /** Partes do PDF, na ordem e com as chaves de RelatorioService::SECOES. */
    val SECOES = listOf(
        "resumo" to "Comparação com o período anterior",
        "recrutamento" to "Recrutamento",
        "financeiro" to "Financeiro",
        "prazos" to "Prazos",
        "atrasados" to "Itens atrasados agora",
        "ranking" to "Ranking por pessoa e por tabela",
        "atividades" to "Tudo o que foi feito",
    )

    /** Padrão para relatórios novos: tudo menos o ranking por pessoa. */
    val SECOES_PADRAO = SECOES.map { it.first }.filter { it != "ranking" }

    val ATALHOS = listOf(
        "7d" to "Últimos 7 dias",
        "30d" to "Últimos 30 dias",
        "mes" to "Este mês",
        "mes-passado" to "Mês passado",
        "outro" to "Escolher datas",
    )

    /** Datas inclusivas do atalho; "outro" devolve null (a pessoa escolhe). */
    fun periodoDoAtalho(atalho: String, hoje: String = Datas.iso()): Pair<String, String>? {
        val cal = Calendar.getInstance().apply { time = Datas.ler(hoje)!! }
        return when (atalho) {
            "7d" -> Datas.somarDias(hoje, -6) to hoje
            "30d" -> Datas.somarDias(hoje, -29) to hoje
            "mes" -> Datas.iso(cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.time) to hoje
            "mes-passado" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val fim = Datas.iso(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                Datas.iso(cal.time) to fim
            }
            else -> null
        }
    }

    /** "01/10 a 07/10/2026". */
    fun textoDoPeriodo(inicio: String?, fim: String?): String {
        if (inicio.isNullOrBlank() || fim.isNullOrBlank()) return ""
        val (ai, mi, di) = inicio.split("-")
        val (af, mf, df) = fim.split("-")
        if (inicio == fim) return "$df/$mf/$af"
        return if (ai == af) "$di/$mi a $df/$mf/$af" else "$di/$mi/$ai a $df/$mf/$af"
    }

    fun numero(n: Double?): String = n?.let {
        NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 2 }.format(it)
    } ?: "—"

    fun moeda(n: Double?): String = n?.let {
        // Igual ao Intl do navegador: "R$ 1.234,50" com espaço comum.
        NumberFormat.getCurrencyInstance(PT_BR).format(it).replace(' ', ' ')
    } ?: "—"

    /** gerarCsv: ";" e BOM (o Excel em português abre direto, com acentos). */
    fun gerarCsv(cabecalho: List<Any?>, linhas: List<List<Any?>>): String =
        "\uFEFF" + (listOf(cabecalho) + linhas).joinToString("\r\n") { linha -> linha.joinToString(";") { celulaCsv(it) } }

    private fun celulaCsv(valor: Any?): String {
        val texto = when (valor) {
            null -> ""
            is Double -> if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()
            else -> valor.toString()
        }
        // Evita injeção de fórmula ao abrir no Excel (=, +, -, @ no começo).
        val seguro = if (Regex("^[=+\\-@\t\r]").containsMatchIn(texto) && !Regex("^-?\\d+([.,]\\d+)?$").matches(texto)) "'$texto" else texto
        return if (Regex("[\";\n]").containsMatchIn(seguro)) "\"" + seguro.replace("\"", "\"\"") + "\"" else seguro
    }

    data class Variacao(val texto: String, val subiu: Boolean?, val bom: Boolean?)

    /** descreverVariacao: "+12%", "novo", "igual"; subirEBom decide a cor. */
    fun variacao(c: Comparacao?, subirEBom: Boolean = true): Variacao? {
        val v = c?.variacao
        if (v == null) return if (c != null && (c.atual ?: 0.0) != 0.0) Variacao("novo", null, null) else null
        if (v == 0.0) return Variacao("igual", null, null)
        val casas = if (abs(v) < 10 && v % 1.0 != 0.0) 1 else 0
        val numero = NumberFormat.getNumberInstance(PT_BR).apply {
            minimumFractionDigits = casas; maximumFractionDigits = casas
        }.format(abs(v))
        return Variacao("${if (v > 0) "+" else "−"}$numero%", v > 0, (v > 0) == subirEBom)
    }
}

/** utils/prazos.js e utils/recorrencia.js: iguais ao PrazoService.php e RecorrenciaService.php do backend. */
object Prazos {
    private val OPCOES_DE_CONCLUSAO = setOf(
        "concluido", "concluida", "feito", "feita", "entregue", "pago", "paga", "finalizado", "finalizada",
        "fechado", "fechada", "cancelado", "cancelada", "perdido", "perdida", "publicado", "publicada",
        "resolvido", "resolvida", "encerrado", "encerrada", "arquivado", "arquivada", "contratado", "contratada",
    )

    /** normalizar de utils/prazos.js: sem acento, minúsculas, só letras e espaço. */
    fun normalizar(texto: String?): String =
        Normalizer.normalize(texto.orEmpty().trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^a-z ]"), "")

    fun opcaoDeConclusao(valor: String?) = valor != null && normalizar(valor) in OPCOES_DE_CONCLUSAO

    /** Concluído quando alguma coluna de lista está numa opção de fim de trabalho. */
    fun itemConcluido(valoresDasListas: List<String?>) = valoresDasListas.any { opcaoDeConclusao(it) }

    enum class Tipo { ATRASADO, HOJE, BREVE, FUTURO, CONCLUIDO }
    data class Situacao(val tipo: Tipo, val rotulo: String)

    /** situacaoDoPrazo: cor pela urgência; item concluído mostra só a data. */
    fun situacao(dataIso: String?, hoje: String = Datas.iso(), concluido: Boolean = false): Situacao? {
        if (dataIso == null || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dataIso)) return null
        val data = Datas.ler(dataIso) ?: return null
        val curta = SimpleDateFormat("dd MMM", PT_BR).format(data).replace(".", "")
        if (concluido) return Situacao(Tipo.CONCLUIDO, curta)
        val dias = ((data.time - Datas.ler(hoje)!!.time).toDouble() / 86_400_000).roundToInt()
        return when {
            dias < 0 -> Situacao(Tipo.ATRASADO, if (dias == -1) "Atrasado desde ontem" else "Atrasado há ${-dias} dias")
            dias == 0 -> Situacao(Tipo.HOJE, "Vence hoje")
            dias == 1 -> Situacao(Tipo.BREVE, "Vence amanhã")
            dias <= 7 -> Situacao(Tipo.BREVE, "Vence em $dias dias")
            else -> Situacao(Tipo.FUTURO, curta)
        }
    }

    /** Opções da coluna "Repetição" (RecorrenciaService::PERIODOS). */
    val REPETICOES = listOf(
        "semanal" to "Toda semana",
        "quinzenal" to "A cada 15 dias",
        "mensal" to "Todo mês",
        "trimestral" to "A cada 3 meses",
        "anual" to "Todo ano",
    )

    fun rotuloDaRepeticao(valor: String?) = REPETICOES.firstOrNull { it.first == valor }?.second
}

/** utils/vinculos.js: colunas de ligação guardam o id (ou ids) de itens de outra tabela. */
object Vinculos {
    fun tituloDoItem(titulo: String?) = if (titulo.isNullOrEmpty()) "Item sem nome" else titulo

    /** Nomes dos itens ligados; sem os nomes (sem acesso ou carregando), quantos são. */
    fun rotulo(ids: List<String>, titulos: Map<String, String?>?): String? {
        if (ids.isEmpty()) return null
        if (titulos == null) return if (ids.size == 1) "1 item ligado" else "${ids.size} itens ligados"
        return ids.joinToString(", ") { if (titulos.containsKey(it)) tituloDoItem(titulos[it]) else "Item excluído" }
    }
}
