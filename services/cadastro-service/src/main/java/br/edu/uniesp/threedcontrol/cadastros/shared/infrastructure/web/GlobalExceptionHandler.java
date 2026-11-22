package br.edu.uniesp.threedcontrol.cadastros.shared.infrastructure.web;

import br.edu.uniesp.threedcontrol.cadastros.shared.domain.DomainException;
import br.edu.uniesp.threedcontrol.cadastros.shared.domain.RecursoNaoEncontradoException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Traduz exceções de domínio/aplicação para respostas HTTP no formato RFC 9457 (Problem Details). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail regraDeNegocio(DomainException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concorrencia(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Conflito de atualização",
                "O registro foi alterado por outra operação ao mesmo tempo. Consulte novamente e repita a ação.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail argumentoInvalido(IllegalArgumentException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException e) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos.");
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(fe.getField(), Objects.requireNonNullElse(fe.getDefaultMessage(), "inválido"));
        }
        pd.setProperty("campos", campos);
        return pd;
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setTitle(titulo);
        return pd;
    }
}
