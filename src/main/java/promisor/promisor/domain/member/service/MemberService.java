package promisor.promisor.domain.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promisor.promisor.domain.member.dao.MemberRepository;
import promisor.promisor.domain.member.dao.RelationRepository;
import promisor.promisor.domain.member.domain.MemberRole;
import promisor.promisor.domain.member.domain.Member;
import promisor.promisor.domain.member.domain.Relation;
import promisor.promisor.domain.member.dto.FollowFriendRequest;
import promisor.promisor.domain.member.dto.SignUpDto;
import promisor.promisor.domain.member.exception.EmailDuplicatedException;
import promisor.promisor.domain.member.exception.MemberEmailNotFound;
import promisor.promisor.domain.member.exception.MembernameNotFoundException;
import promisor.promisor.global.error.ErrorCode;
import promisor.promisor.global.token.exception.TokenExpiredException;
import promisor.promisor.global.token.ConfirmationToken;
import promisor.promisor.global.token.ConfirmationTokenService;
import promisor.promisor.global.token.exception.TokenNotExistException;
import promisor.promisor.infra.email.EmailSender;
import promisor.promisor.infra.email.EmailValidator;
import promisor.promisor.infra.email.exception.EmailConfirmedException;
import promisor.promisor.infra.email.exception.EmailNotValid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class MemberService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final EmailValidator emailValidator;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final ConfirmationTokenService confirmationTokenService;
    private final EmailSender emailSender;
    private final RelationRepository relationRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

//        TODO: 여기서 NPE 예외처리를 해줘야 하나?
        Member member = memberRepository.findByEmail(email);

        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(member.getRole()));
        return new User(member.getEmail(), member.getPassword(), authorities);
    }

    @Transactional
    public String save(SignUpDto request) {
        boolean isValidEmail = emailValidator.test(request.getEmail());

        if (!isValidEmail) {
            throw new EmailNotValid(request.getEmail());
        }

        String token = signUpUser(
               Member.of(
                        request.getName(),
                        request.getEmail(),
                        request.getPassword(),
                        request.getTelephone(),
                        MemberRole.USER
                )
        );

        String link = "http://localhost:8080/members/confirm?token=" + token;
        emailSender.send(request.getEmail(), buildEmail(request.getName(), link));

        return token;
    }

    @Transactional
    public String signUpUser(Member member) {
        Member userExists = memberRepository.findByEmail(member.getEmail());

        if (userExists != null) {
            // TODO check of attributes are the same and
            // TODO if email not confirmed send confirmation email.
            throw new EmailDuplicatedException(member.getEmail());
        }

        String encodedPassword = bCryptPasswordEncoder.encode(member.getPassword());
        member.setEncodedPassword(encodedPassword);

        memberRepository.save(member);

        String token = UUID.randomUUID().toString();

        ConfirmationToken confirmationToken = ConfirmationToken.of(
                token,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15),
                member
                );

        confirmationTokenService.saveConfirmationToken(confirmationToken);

        return token;
    }

    @Transactional
    public String confirmToken(String token) {
        ConfirmationToken confirmationToken = confirmationTokenService
                .getToken(token)
                .orElseThrow(TokenNotExistException::new);

        if (confirmationToken.getConfirmedAt() != null) {
            throw new EmailConfirmedException();
        }

        LocalDateTime expiredAt = confirmationToken.getExpiresAt();

        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException();
        }

        confirmationTokenService.setConfirmedAt(token);
        memberRepository.enableMember(confirmationToken.getMember().getEmail());
        return "인증되었습니다.";
    }

    public String buildEmail(String name, String link) {
        return "<div style=\"font-family:Helvetica,Arial,sans-serif;font-size:16px;margin:0;color:#0b0c0c\">\n" +
                "\n" +
                "<span style=\"display:none;font-size:1px;color:#fff;max-height:0\"></span>\n" +
                "\n" +
                "  <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;min-width:100%;width:100%!important\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n" +
                "    <tbody><tr>\n" +
                "      <td width=\"100%\" height=\"53\" bgcolor=\"#0b0c0c\">\n" +
                "        \n" +
                "        <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;max-width:580px\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\">\n" +
                "          <tbody><tr>\n" +
                "            <td width=\"70\" bgcolor=\"#0b0c0c\" valign=\"middle\">\n" +
                "                <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n" +
                "                  <tbody><tr>\n" +
                "                    <td style=\"padding-left:10px\">\n" +
                "                  \n" +
                "                    </td>\n" +
                "                    <td style=\"font-size:28px;line-height:1.315789474;Margin-top:4px;padding-left:10px\">\n" +
                "                      <span style=\"font-family:Helvetica,Arial,sans-serif;font-weight:700;color:#ffffff;text-decoration:none;vertical-align:top;display:inline-block\">Confirm your email</span>\n" +
                "                    </td>\n" +
                "                  </tr>\n" +
                "                </tbody></table>\n" +
                "              </a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </tbody></table>\n" +
                "        \n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </tbody></table>\n" +
                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n" +
                "    <tbody><tr>\n" +
                "      <td width=\"10\" height=\"10\" valign=\"middle\"></td>\n" +
                "      <td>\n" +
                "        \n" +
                "                <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n" +
                "                  <tbody><tr>\n" +
                "                    <td bgcolor=\"#1D70B8\" width=\"100%\" height=\"10\"></td>\n" +
                "                  </tr>\n" +
                "                </tbody></table>\n" +
                "        \n" +
                "      </td>\n" +
                "      <td width=\"10\" valign=\"middle\" height=\"10\"></td>\n" +
                "    </tr>\n" +
                "  </tbody></table>\n" +
                "\n" +
                "\n" +
                "\n" +
                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n" +
                "    <tbody><tr>\n" +
                "      <td height=\"30\"><br></td>\n" +
                "    </tr>\n" +
                "    <tr>\n" +
                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                "      <td style=\"font-family:Helvetica,Arial,sans-serif;font-size:19px;line-height:1.315789474;max-width:560px\">\n" +
                "        \n" +
                "            <p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\">안녕하세요 " + name +"님,</p><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> 가입해주셔서 감사합니다. 아래의 링크를 클릭해서 계정 인증을 완료해 주세요: </p><blockquote style=\"Margin:0 0 20px 0;border-left:10px solid #b1b4b6;padding:15px 0 0.1px 15px;font-size:19px;line-height:25px\"><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> <a href=\"" + link + "\">인증 활성화</a> </p></blockquote>\n 해당 링크는 15분 뒤에 만료됩니다. <p>반갑습니다!</p>" +
                "        \n" +
                "      </td>\n" +
                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                "    </tr>\n" +
                "    <tr>\n" +
                "      <td height=\"30\"><br></td>\n" +
                "    </tr>\n" +
                "  </tbody></table><div class=\"yj6qo\"></div><div class=\"adL\">\n" +
                "\n" +
                "</div></div>";
    }

    public Member getMember(String email) {
        log.info("Fetching member '{}'", email);
        return memberRepository.findByEmail(email);
//        TODO: 여기서 NPE 예외처리를 해줘야 하나?
    }

    @Transactional
    public void followFriend(String email, FollowFriendRequest request) {
        Member requester = getMember(email);
        Member receiver  = memberRepository.findByEmail(request.getReceiverEmail());
        if (receiver != null) {
            throw new MemberEmailNotFound();
        }
        if (requester.hasFriend(receiver) || relationRepository.existByOwnerEmailAndFriendEmail(email, request.getReceiverEmail())) {
            throw new EmailDuplicatedException(request.getReceiverEmail());
        }
        relationRepository.save(new Relation(requester, receiver));
    }
}