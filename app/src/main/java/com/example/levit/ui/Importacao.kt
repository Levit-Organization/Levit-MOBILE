package com.example.levit.ui

import com.example.levit.data.remote.dto.Coluna
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Importação de planilhas (utils/importacao.js). Tudo puro, sem rede: ler o
 * CSV ou o XLSX, casar as colunas da planilha com as da tabela e converter cada
 * célula para o formato que a API valida.
 */
object Importacao {

    /** Data vinda de célula de data do Excel (o web recebe um Date em UTC). */
    data class DataExcel(val iso: String)

    data class Problema(val linha: Int, val mensagem: String)
    data class Preparo(val itens: List<Pair<Int, Map<String, Any>>>, val problemas: List<Problema>)

    /** UTF-8 quando válido; senão Windows-1252 (CSV salvo pelo Excel em português). */
    fun decodificarTexto(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }

    /** Separador mais provável na primeira linha (o Excel brasileiro usa ponto e vírgula). */
    private fun separador(texto: String): Char {
        val primeira = texto.split(Regex("\\r?\\n"), limit = 2).firstOrNull().orEmpty()
        val (sep, n) = listOf(';', ',', '\t').map { s -> s to primeira.count { it == s } }.maxBy { it.second }
        return if (n > 0) sep else ','
    }

    /** CSV → matriz, com aspas, aspas duplicadas e quebras de linha dentro de aspas. */
    fun lerCsv(texto: String): List<List<Any?>> {
        val sep = separador(texto)
        val linhas = mutableListOf<List<Any?>>()
        var linha = mutableListOf<Any?>()
        val celula = StringBuilder()
        var aspas = false
        var i = 0
        while (i < texto.length) {
            val ch = texto[i]
            if (aspas) {
                when {
                    ch == '"' && texto.getOrNull(i + 1) == '"' -> { celula.append('"'); i++ }
                    ch == '"' -> aspas = false
                    else -> celula.append(ch)
                }
            } else when (ch) {
                '"' -> aspas = true
                sep -> { linha += celula.toString(); celula.clear() }
                '\n', '\r' -> {
                    if (ch == '\r' && texto.getOrNull(i + 1) == '\n') i++
                    linha += celula.toString(); celula.clear()
                    linhas += linha; linha = mutableListOf()
                }
                else -> celula.append(ch)
            }
            i++
        }
        if (celula.isNotEmpty() || linha.isNotEmpty()) { linha += celula.toString(); linhas += linha }
        return linhas.filter { l -> l.any { it.toString().isNotBlank() } }
    }

    /** XLSX (zip com XML): a primeira planilha, como faz o read-excel-file do web. */
    fun lerXlsx(bytes: ByteArray): List<List<Any?>> {
        val arquivos = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e -> if (!e.isDirectory) arquivos[e.name] = zip.readBytes() }
        }
        fun xml(nome: String) = arquivos[nome]?.let {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(ByteArrayInputStream(it))
        }
        fun Element.filhos(tag: String) = getElementsByTagName(tag).let { l -> (0 until l.length).map { l.item(it) as Element } }

        val compartilhados = xml("xl/sharedStrings.xml")?.documentElement?.filhos("si")?.map { si ->
            si.filhos("t").joinToString("") { it.textContent }
        }.orEmpty()

        // Estilos de data: formatos embutidos 14–22 e 45–47, ou personalizados com d/m/a.
        val estilos = xml("xl/styles.xml")?.documentElement
        val formatosProprios = estilos?.filhos("numFmt")?.associate { it.getAttribute("numFmtId").toInt() to it.getAttribute("formatCode") }.orEmpty()
        val xfs = estilos?.filhos("cellXfs")?.firstOrNull()?.filhos("xf")?.map { it.getAttribute("numFmtId").toIntOrNull() ?: 0 }.orEmpty()
        fun ehData(estilo: Int): Boolean {
            val fmt = xfs.getOrNull(estilo) ?: return false
            if (fmt in 14..22 || fmt in 45..47) return true
            val codigo = formatosProprios[fmt]?.replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "")?.lowercase() ?: return false
            return Regex("[dmy]").containsMatchIn(codigo) && !codigo.contains("#")
        }

        // Primeira planilha pela ordem do workbook.
        val workbook = xml("xl/workbook.xml")?.documentElement
        val relId = workbook?.filhos("sheet")?.firstOrNull()?.getAttribute("r:id")
        val alvo = xml("xl/_rels/workbook.xml.rels")?.documentElement?.filhos("Relationship")
            ?.firstOrNull { it.getAttribute("Id") == relId }?.getAttribute("Target")
            ?.let { if (it.startsWith("/")) it.drop(1) else "xl/$it" } ?: "xl/worksheets/sheet1.xml"
        val planilha = xml(alvo)?.documentElement ?: return emptyList()

        val linhas = planilha.filhos("row").map { row ->
            val celulas = sortedMapOf<Int, Any?>()
            row.filhos("c").forEach { c ->
                val col = c.getAttribute("r").takeWhile { it.isLetter() }.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
                val v = c.filhos("v").firstOrNull()?.textContent
                val valor: Any? = when (c.getAttribute("t")) {
                    "s" -> v?.toIntOrNull()?.let { compartilhados.getOrNull(it) }
                    "inlineStr" -> c.filhos("t").joinToString("") { it.textContent }
                    "str" -> v
                    "b" -> v == "1"
                    else -> v?.toDoubleOrNull()?.let { n ->
                        if (ehData(c.getAttribute("s").toIntOrNull() ?: 0)) DataExcel(dataDoExcel(n)) else n
                    }
                }
                if (col >= 0) celulas[col] = valor
            }
            val ultima = celulas.keys.maxOrNull() ?: -1
            (0..ultima).map { celulas[it] }
        }
        return linhas.filter { l -> l.any { it != null && it.toString().isNotBlank() } }
    }

    /** Número de série do Excel (sistema 1900) para AAAA-MM-DD. */
    private fun dataDoExcel(serie: Double): String {
        val dias = kotlin.math.floor(serie).toLong()
        val ms = (dias - 25569) * 86_400_000L
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(ms))
    }

    /** Para cada coluna da tabela, o índice da coluna da planilha com o mesmo nome (ou -1). */
    fun mapearColunas(cabecalho: List<String>, campos: List<Coluna>): MutableMap<String, Int> {
        val nomes = cabecalho.map { Prazos.normalizar(it) }
        return campos.associate { it.id.orEmpty() to nomes.indexOf(Prazos.normalizar(it.nome)) }.toMutableMap()
    }

    private fun validarData(ano: Int, mes: Int, dia: Int): String? {
        val cal = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            isLenient = true; clear(); set(ano, mes - 1, dia)
        }
        return if (cal.get(java.util.Calendar.YEAR) == ano && cal.get(java.util.Calendar.MONTH) == mes - 1 &&
            cal.get(java.util.Calendar.DAY_OF_MONTH) == dia) "%04d-%02d-%02d".format(ano, mes, dia) else null
    }

    private fun converterData(bruto: Any): String? {
        if (bruto is DataExcel) return bruto.iso
        val t = bruto.toString().trim()
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(t)?.let { m ->
            return validarData(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2}|\\d{4})$").find(t)?.let { m ->
            val ano = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            return validarData(ano, m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        return null
    }

    private fun converterNumero(bruto: Any): Double? {
        if (bruto is Double) return bruto.takeIf { it.isFinite() }
        var t = bruto.toString().replace(Regex("R\\$|\\s"), "").trim()
        if (t.isEmpty()) return null
        // "1.234,56" (Brasil) ou "1,234.56" (inglês): o último separador é o decimal.
        t = if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replaceFirst(",", ".") else t.replace(",", "")
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** String(x) do JavaScript: número inteiro sem ".0". */
    private fun textoJs(bruto: Any): String = when (bruto) {
        is Double -> if (bruto % 1.0 == 0.0 && kotlin.math.abs(bruto) < 1e15) bruto.toLong().toString() else bruto.toString()
        is DataExcel -> bruto.iso
        else -> bruto.toString()
    }

    /** Converte a célula para o tipo da coluna. (valor, erro); valor e erro nulos = célula vazia. */
    fun converterCelula(
        bruto: Any?,
        campo: Coluna,
        pessoas: List<Pair<String, String>>,
        ligados: Map<String, Map<String, String?>>
    ): Pair<Any?, String?> {
        if (bruto == null || textoJs(bruto).isBlank()) return null to null
        val texto = textoJs(bruto).trim()
        return when (campo.tipo) {
            "numero" -> converterNumero(if (bruto is Double) bruto else texto)?.let { it to null } ?: (null to "\"$texto\" não é um número")
            "data", "prazo" -> converterData(if (bruto is DataExcel) bruto else texto)?.let { it to null }
                ?: (null to "\"$texto\" não é uma data (use DD/MM/AAAA)")
            "selecao" -> campo.listaDeOpcoes.firstOrNull { Prazos.normalizar(it) == Prazos.normalizar(texto) }?.let { it to null }
                ?: (null to "\"$texto\" não é uma opção de ${campo.nome}")
            "responsavel" -> {
                val alvo = Prazos.normalizar(texto)
                val achadas = pessoas.filter { (_, nome) -> Prazos.normalizar(nome) == alvo || Prazos.normalizar(nome.split(" ").first()) == alvo }
                if (achadas.size == 1) achadas[0].first to null
                else null to if (achadas.size > 1) "\"$texto\" é ambíguo na equipe" else "\"$texto\" não está na equipe"
            }
            "recorrencia" -> {
                val alvo = Prazos.normalizar(texto)
                Prazos.REPETICOES.firstOrNull { it.first == alvo || Prazos.normalizar(it.second) == alvo }?.let { it.first to null }
                    ?: (null to "\"$texto\" não é uma repetição válida")
            }
            "vinculo" -> converterLigacao(texto, campo, ligados[campo.moduloVinculadoId])
            else -> texto to null
        }
    }

    /** Ligação: a célula traz o nome do item; vários = nomes separados por vírgula ou ponto e vírgula. */
    private fun converterLigacao(texto: String, campo: Coluna, titulos: Map<String, String?>?): Pair<Any?, String?> {
        if (titulos == null) return null to "não foi possível ler os itens da tabela ligada a ${campo.nome}"
        fun achar(nome: String) = titulos.keys.filter { Prazos.normalizar(titulos[it].orEmpty()) == Prazos.normalizar(nome) }
        val inteiro = achar(texto)
        if (inteiro.size == 1) return (if (campo.multiplo) inteiro else inteiro[0]) to null
        if (inteiro.size > 1) return null to "\"$texto\" é ambíguo na tabela ligada"
        if (!campo.multiplo) return null to "\"$texto\" não está na tabela ligada"
        val ids = mutableListOf<String>()
        for (parte in texto.split(Regex("[;,]")).map { it.trim() }.filter { it.isNotEmpty() }) {
            val achados = achar(parte)
            if (achados.size != 1) return null to "\"$parte\" ${if (achados.isNotEmpty()) "é ambíguo" else "não está"} na tabela ligada"
            ids += achados[0]
        }
        return ids.distinct() to null
    }

    /** prepararItens: linhas com alguma célula inválida vão para problemas e não são enviadas. */
    fun prepararItens(
        linhas: List<List<Any?>>,
        campos: List<Coluna>,
        mapa: Map<String, Int>,
        pessoas: List<Pair<String, String>>,
        ligados: Map<String, Map<String, String?>>
    ): Preparo {
        val itens = mutableListOf<Pair<Int, Map<String, Any>>>()
        val problemas = mutableListOf<Problema>()
        linhas.forEachIndexed { i, celulas ->
            val linha = i + 2 // a linha 1 é o cabeçalho
            val dados = mutableMapOf<String, Any>()
            val erros = mutableListOf<String>()
            campos.forEach { campo ->
                val indice = mapa[campo.id.orEmpty()] ?: -1
                if (indice < 0) return@forEach
                val (valor, erro) = converterCelula(celulas.getOrNull(indice), campo, pessoas, ligados)
                if (erro != null) erros += "${campo.nome}: $erro" else if (valor != null) dados[campo.id.orEmpty()] = valor
            }
            if (erros.isNotEmpty()) problemas += Problema(linha, erros.joinToString("; "))
            else if (dados.isNotEmpty()) itens += linha to dados
        }
        return Preparo(itens, problemas)
    }
}
