package br.edu.uniesp.threedcontrol.pedidos.pedido.domain;

import br.edu.uniesp.threedcontrol.pedidos.pedido.domain.Enums.CanalAtendimento;
import br.edu.uniesp.threedcontrol.pedidos.pedido.domain.Enums.CondicaoPagamento;
import br.edu.uniesp.threedcontrol.pedidos.pedido.domain.Enums.ModalidadeEntrega;
import br.edu.uniesp.threedcontrol.pedidos.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Raiz do agregado Pedido. Concentra as regras centrais do negócio:
 * <ul>
 *   <li>Regra de Ouro: alterações só antes da produção;</li>
 *   <li>Máquina de estados do ciclo de vida, com histórico (rastreio);</li>
 *   <li>Entrega condicionada à quitação do saldo.</li>
 * </ul>
 */
public class Pedido {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UUID id;
    private final String codigo;
    private final UUID clienteId;
    private final CanalAtendimento canalOrigem;
    private final String atendenteResponsavel;
    private LocalDate prazoEntrega;
    private ModalidadeEntrega modalidadeEntrega;
    private CondicaoPagamento condicaoPagamento;
    private String observacoes;
    private List<ItemPedido> itens;
    private StatusPedido status;
    private final List<HistoricoStatus> historico;
    private final LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private final Long versao;

    private Pedido(UUID id, String codigo, UUID clienteId, CanalAtendimento canalOrigem, String atendenteResponsavel,
                   LocalDate prazoEntrega, ModalidadeEntrega modalidadeEntrega, CondicaoPagamento condicaoPagamento,
                   String observacoes, List<ItemPedido> itens, StatusPedido status, List<HistoricoStatus> historico,
                   LocalDateTime criadoEm, LocalDateTime atualizadoEm, Long versao) {
        this.id = id;
        this.codigo = codigo;
        this.clienteId = clienteId;
        this.canalOrigem = canalOrigem;
        this.atendenteResponsavel = atendenteResponsavel;
        this.prazoEntrega = prazoEntrega;
        this.modalidadeEntrega = modalidadeEntrega;
        this.condicaoPagamento = condicaoPagamento;
        this.observacoes = observacoes;
        this.itens = itens;
        this.status = status;
        this.historico = historico;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
        this.versao = versao;
    }

    public static Pedido registrar(UUID clienteId, CanalAtendimento canalOrigem, String atendenteResponsavel,
                                   LocalDate prazoEntrega, ModalidadeEntrega modalidadeEntrega,
                                   CondicaoPagamento condicaoPagamento, String observacoes, List<ItemPedido> itens) {
        if (clienteId == null) {
            throw new DomainException("O cliente é obrigatório.");
        }
        if (canalOrigem == null) {
            throw new DomainException("O canal de origem do pedido é obrigatório.");
        }
        if (canalOrigem != CanalAtendimento.SITE && (atendenteResponsavel == null || atendenteResponsavel.isBlank())) {
            throw new DomainException("Informe o funcionário que realizou o atendimento.");
        }
        LocalDateTime agora = LocalDateTime.now();
        String responsavel = canalOrigem == CanalAtendimento.SITE && (atendenteResponsavel == null || atendenteResponsavel.isBlank())
                ? "Site (pedido automatizado)" : atendenteResponsavel;
        Pedido p = new Pedido(UUID.randomUUID(), gerarCodigo(agora), clienteId, canalOrigem, responsavel,
                null, null, null, null, List.of(), StatusPedido.EM_TRIAGEM, new ArrayList<>(), agora, agora, null);
        p.definirDetalhes(prazoEntrega, modalidadeEntrega, condicaoPagamento, observacoes, itens);
        p.historico.add(new HistoricoStatus(StatusPedido.EM_TRIAGEM, agora, "Pedido registrado via " + canalOrigem));
        return p;
    }

    public static Pedido restaurar(UUID id, String codigo, UUID clienteId, CanalAtendimento canalOrigem,
                                   String atendenteResponsavel, LocalDate prazoEntrega,
                                   ModalidadeEntrega modalidadeEntrega, CondicaoPagamento condicaoPagamento,
                                   String observacoes, List<ItemPedido> itens, StatusPedido status,
                                   List<HistoricoStatus> historico, LocalDateTime criadoEm,
                                   LocalDateTime atualizadoEm, Long versao) {
        return new Pedido(id, codigo, clienteId, canalOrigem, atendenteResponsavel, prazoEntrega, modalidadeEntrega,
                condicaoPagamento, observacoes, List.copyOf(itens), status, new ArrayList<>(historico),
                criadoEm, atualizadoEm, versao);
    }

    // ------------------------------------------------------------------ Regra de Ouro

    public void garantirAlteravel() {
        if (!status.permiteAlteracao()) {
            throw new PedidoNaoAlteravelException(codigo, status);
        }
    }

    public void alterar(LocalDate prazoEntrega, ModalidadeEntrega modalidadeEntrega,
                        CondicaoPagamento condicaoPagamento, String observacoes, List<ItemPedido> itens) {
        garantirAlteravel();
        definirDetalhes(prazoEntrega, modalidadeEntrega, condicaoPagamento, observacoes, itens);
        registrar(status, "Pedido alterado pelo atendimento");
    }

    private void definirDetalhes(LocalDate prazoEntrega, ModalidadeEntrega modalidadeEntrega,
                                 CondicaoPagamento condicaoPagamento, String observacoes, List<ItemPedido> itens) {
        if (prazoEntrega == null || prazoEntrega.isBefore(LocalDate.now())) {
            throw new DomainException("O prazo de entrega deve ser hoje ou uma data futura.");
        }
        if (modalidadeEntrega == null || condicaoPagamento == null) {
            throw new DomainException("Modalidade de entrega e condição de pagamento são obrigatórias.");
        }
        if (itens == null || itens.isEmpty()) {
            throw new DomainException("O pedido deve ter ao menos um item.");
        }
        this.prazoEntrega = prazoEntrega;
        this.modalidadeEntrega = modalidadeEntrega;
        this.condicaoPagamento = condicaoPagamento;
        this.observacoes = observacoes;
        this.itens = List.copyOf(itens);
    }

    // ------------------------------------------------------------------ Ciclo de vida

    /** Ajusta o status conforme o resultado da reserva de insumos no estoque. */
    public void atualizarDisponibilidadeInsumos(boolean disponiveis, String detalhe) {
        if (disponiveis && status == StatusPedido.AGUARDANDO_INSUMO) {
            transitarPara(StatusPedido.EM_TRIAGEM, "Insumos reservados");
        } else if (!disponiveis && status == StatusPedido.EM_TRIAGEM) {
            transitarPara(StatusPedido.AGUARDANDO_INSUMO, detalhe);
        }
    }

    public void iniciarProducao() {
        if (status == StatusPedido.AGUARDANDO_INSUMO) {
            throw new DomainException("O pedido " + codigo + " ainda aguarda insumos e não pode entrar em produção.");
        }
        transitarPara(StatusPedido.EM_PRODUCAO, "Produção iniciada — pedido bloqueado para alterações");
    }

    public void finalizarProducao() {
        transitarPara(StatusPedido.FINALIZADO, "Produção concluída");
    }

    public void despachar() {
        if (modalidadeEntrega != ModalidadeEntrega.ENTREGA) {
            throw new DomainException("O pedido " + codigo + " é para retirada e não pode ser despachado.");
        }
        transitarPara(StatusPedido.EM_ROTA_ENTREGA, "Saiu para entrega");
    }

    /**
     * Validação na entrega: o produto só é liberado ao cliente com o saldo integralmente quitado.
     */
    public void confirmarEntrega(boolean pagamentoQuitado, BigDecimal saldoRemanescente) {
        if (!pagamentoQuitado) {
            throw new DomainException("Entrega bloqueada: o pedido " + codigo + " possui saldo remanescente de R$ "
                    + saldoRemanescente + ". Registre a quitação no financeiro antes de liberar o produto.");
        }
        transitarPara(StatusPedido.ENTREGUE, "Entregue ao cliente com pagamento quitado");
    }

    public void cancelar(String motivo) {
        transitarPara(StatusPedido.CANCELADO, motivo == null || motivo.isBlank() ? "Pedido cancelado" : motivo);
    }

    private void transitarPara(StatusPedido destino, String observacao) {
        if (!status.podeTransitarPara(destino)) {
            throw new DomainException("Transição de status inválida para o pedido " + codigo + ": "
                    + status.descricao() + " → " + destino.descricao() + ".");
        }
        status = destino;
        registrar(destino, observacao);
    }

    private void registrar(StatusPedido s, String observacao) {
        atualizadoEm = LocalDateTime.now();
        historico.add(new HistoricoStatus(s, atualizadoEm, observacao));
    }

    // ------------------------------------------------------------------ Cálculos

    public BigDecimal valorTotal() {
        return itens.stream().map(ItemPedido::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal custoEstimadoTotal() {
        return itens.stream().map(ItemPedido::custoTotalEstimado).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String gerarCodigo(LocalDateTime agora) {
        StringBuilder sufixo = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sufixo.append(ALFABETO.charAt(ThreadLocalRandom.current().nextInt(ALFABETO.length())));
        }
        return "3DC-" + agora.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + sufixo;
    }

    public UUID getId() { return id; }
    public String getCodigo() { return codigo; }
    public UUID getClienteId() { return clienteId; }
    public CanalAtendimento getCanalOrigem() { return canalOrigem; }
    public String getAtendenteResponsavel() { return atendenteResponsavel; }
    public LocalDate getPrazoEntrega() { return prazoEntrega; }
    public ModalidadeEntrega getModalidadeEntrega() { return modalidadeEntrega; }
    public CondicaoPagamento getCondicaoPagamento() { return condicaoPagamento; }
    public String getObservacoes() { return observacoes; }
    public List<ItemPedido> getItens() { return itens; }
    public StatusPedido getStatus() { return status; }
    public List<HistoricoStatus> getHistorico() { return Collections.unmodifiableList(historico); }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    /** Versão do agregado, usada para controle de concorrência otimista. */
    public Long getVersao() { return versao; }
}
