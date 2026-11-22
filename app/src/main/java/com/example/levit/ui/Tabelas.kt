package com.example.levit.ui

/*
 * Catálogos da área de Tabelas, iguais aos do web (ModuleForm.jsx,
 * utils/iconOptions.js e utils/modelosTabela.js). Os ícones são nomes do
 * Material Icons, desenhados pela mesma fonte do web (res/font/material_icons).
 */

data class TipoTabela(val valor: String, val rotulo: String, val icone: String, val descricao: String)

data class TipoColuna(val valor: String, val rotulo: String, val icone: String, val dica: String? = null)

/** Coluna de um modelo (opcoes só nas colunas de lista). */
data class ColunaModelo(val nome: String, val tipo: String = "texto", val opcoes: List<String>? = null)

data class ModeloTabela(
    val id: String,
    val titulo: String,
    val descricao: String,
    val icone: String,
    val tipo: String,
    val nome: String,
    val colunas: List<ColunaModelo>,
    val segmento: String? = null,
    val fases: List<String>? = null
)

object Tabelas {

    val TIPOS = listOf(
        TipoTabela("dados", "Uma lista do seu jeito", "table_rows",
            "Clientes, pedidos, estoque, tarefas: você escolhe as colunas e pode ver como lista ou quadro."),
        TipoTabela("arquivo", "Arquivos e documentos", "folder_open",
            "Um lugar para enviar e guardar arquivos da equipe, como contratos e comprovantes."),
        TipoTabela("recrutamento", "Vaga de recrutamento", "groups",
            "Candidatos se inscrevem por um link e passam pelas etapas que você definir."),
        TipoTabela("financeiro", "Entradas e saídas", "account_balance_wallet",
            "Controle de dinheiro que entra e sai, com categorias e saldo do período."),
    )

    val TIPOS_DE_COLUNA = listOf(
        TipoColuna("texto", "Texto", "text_fields"),
        TipoColuna("numero", "Número", "tag"),
        TipoColuna("data", "Data", "event"),
        TipoColuna("selecao", "Lista de opções", "list_alt"),
        TipoColuna("responsavel", "Responsável", "person",
            "A pessoa vê os itens dela em \"Meus itens\" e pode ser avisada pelas automações."),
        TipoColuna("prazo", "Prazo", "event_available",
            "Itens atrasados aparecem em \"Precisa da sua atenção\" no Início."),
        TipoColuna("recorrencia", "Repetição", "event_repeat",
            "Ao concluir o item, o Levit cria o próximo com o prazo avançado."),
        TipoColuna("vinculo", "Ligação com tabela", "link",
            "Cada item aponta para itens de outra tabela, como o cliente de um pedido. Dá para filtrar pelos dados da tabela ligada."),
    )

    /** Rótulo curto do tipo, ao lado do nome da coluna no formulário do item (TIPOS de ModuleRecords.jsx). */
    val TIPO_NO_FORMULARIO = mapOf(
        "texto" to "Texto", "numero" to "Número", "data" to "Data", "selecao" to "Seleção",
        "responsavel" to "Pessoa da equipe", "prazo" to "Data", "recorrencia" to "Repetição", "vinculo" to "Ligação",
    )

    fun tipoDaColuna(valor: String?) = TIPOS_DE_COLUNA.firstOrNull { it.valor == valor } ?: TIPOS_DE_COLUNA.first()

    val ICONES = listOf(
        "extension", "group", "business", "work", "dashboard", "settings",
        "inventory_2", "account_balance", "event", "article", "description",
        "folder", "star", "shopping_cart", "person", "local_shipping",
    )

    private fun lista(nome: String, vararg opcoes: String) = ColunaModelo(nome, "selecao", opcoes.toList())
    private fun coluna(nome: String, tipo: String = "texto") = ColunaModelo(nome, tipo)
    private fun responsavel(nome: String = "Responsável") = coluna(nome, "responsavel")
    private fun prazo(nome: String = "Prazo") = coluna(nome, "prazo")
    private fun repeticao(nome: String = "Repete") = coluna(nome, "recorrencia")

    val MODELOS = listOf(
        ModeloTabela("obrigacoes-contabeis", "Obrigações dos clientes",
            "Para escritórios contábeis: cada guia e declaração com responsável e prazo.",
            "request_quote", "dados", "Obrigações dos clientes", listOf(
                coluna("Cliente"),
                lista("Obrigação", "DAS", "Folha de pagamento", "DCTFWeb", "EFD-Reinf", "FGTS Digital", "Declaração de IR", "Outra"),
                coluna("Competência"),
                responsavel(),
                prazo("Vencimento"),
                repeticao(),
                lista("Status", "A fazer", "Aguardando cliente", "Em andamento", "Entregue"),
            ), segmento = "Escritório contábil"),
        ModeloTabela("clientes-escritorio", "Carteira de clientes",
            "Para escritórios contábeis: quem cuida de cada cliente e o regime de cada um.",
            "apartment", "dados", "Carteira de clientes", listOf(
                coluna("Empresa"),
                coluna("CNPJ"),
                lista("Regime", "Simples Nacional", "Lucro Presumido", "Lucro Real", "MEI"),
                responsavel("Responsável pela conta"),
                coluna("Honorário mensal", "numero"),
                lista("Situação", "Ativo", "Em implantação", "Encerrado"),
            ), segmento = "Escritório contábil"),
        ModeloTabela("entregas-agencia", "Entregas dos clientes",
            "Para agências: cada peça do briefing à publicação, com responsável e prazo.",
            "campaign", "dados", "Entregas dos clientes", listOf(
                coluna("Entrega"),
                coluna("Cliente"),
                lista("Tipo", "Post", "Campanha", "Vídeo", "Site", "Relatório", "Outro"),
                responsavel(),
                prazo("Entrega"),
                lista("Etapa", "Briefing", "Produção", "Aprovação do cliente", "Ajustes", "Publicado"),
            ), segmento = "Agência de marketing"),
        ModeloTabela("contratos-agencia", "Clientes e contratos",
            "Para agências: contratos, valores e quando cada um precisa ser renovado.",
            "handshake", "dados", "Clientes e contratos", listOf(
                coluna("Cliente"),
                lista("Serviço", "Social media", "Tráfego pago", "Site", "Branding", "Pacote completo"),
                coluna("Valor mensal", "numero"),
                responsavel("Atendimento"),
                prazo("Renovação"),
                lista("Situação", "Proposta", "Ativo", "Renovar", "Encerrado"),
            ), segmento = "Agência de marketing"),
        ModeloTabela("clientes", "Clientes e vendas",
            "Negócios por etapa, do primeiro contato ao fechamento.",
            "business", "dados", "Clientes e vendas", listOf(
                coluna("Empresa"),
                coluna("Contato"),
                coluna("E-mail"),
                coluna("Valor", "numero"),
                lista("Status", "Novo", "Proposta", "Fechado", "Perdido"),
                coluna("Data de fechamento", "data"),
            )),
        ModeloTabela("tarefas", "Tarefas da equipe",
            "Quem faz o quê e até quando, num quadro de etapas.",
            "dashboard", "dados", "Tarefas da equipe", listOf(
                coluna("Tarefa"),
                responsavel(),
                lista("Etapa", "A fazer", "Fazendo", "Feito"),
                lista("Prioridade", "Baixa", "Média", "Alta"),
                prazo(),
            )),
        ModeloTabela("estoque", "Estoque",
            "Produtos, quantidades e o que está acabando.",
            "inventory_2", "dados", "Estoque", listOf(
                coluna("Produto"),
                coluna("Código"),
                coluna("Quantidade", "numero"),
                coluna("Estoque mínimo", "numero"),
                lista("Situação", "Disponível", "Acabando", "Em falta"),
                coluna("Última compra", "data"),
            )),
        ModeloTabela("pedidos", "Pedidos internos",
            "Pedidos entre setores, com prioridade e prazo.",
            "shopping_cart", "dados", "Pedidos internos", listOf(
                coluna("Pedido"),
                coluna("Pedido por"),
                lista("Setor", "Comercial", "Financeiro", "Operações", "Pessoas"),
                lista("Status", "Aberto", "Em andamento", "Concluído"),
                lista("Prioridade", "Baixa", "Média", "Alta"),
                responsavel(),
                prazo(),
            )),
        ModeloTabela("vaga", "Vaga de recrutamento",
            "Candidatos se inscrevem por um link e passam pelas etapas.",
            "work", "recrutamento", "", emptyList(),
            fases = listOf("Triagem", "Entrevista", "Proposta", "Contratado")),
    )

    /** Modelos agrupados: primeiro os segmentos em que o Levit foca, depois os usos gerais. */
    val GRUPOS_DE_MODELOS: List<Pair<String, List<ModeloTabela>>> =
        MODELOS.groupBy { it.segmento ?: "Outros usos" }.toList()
}
