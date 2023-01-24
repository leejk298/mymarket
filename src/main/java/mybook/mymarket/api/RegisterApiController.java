package mybook.mymarket.api;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import mybook.mymarket.controller.dto.RegisterDto;
import mybook.mymarket.domain.Register;
import mybook.mymarket.repository.RegisterRepository;
import mybook.mymarket.repository.RegisterSearch;
import mybook.mymarket.repository.register.query.RegisterQueryDto;
import mybook.mymarket.repository.register.query.RegisterQueryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;


@RestController // @RestController == @Controller + @ResponseBody
// @ResponseBody : data 자체를 바로 Json 이나 XML 로 바로 보내기 위해
@RequiredArgsConstructor // final 키워드를 가진 필드(memberService)로 생성자를 만들어줌
public class RegisterApiController {
    private final RegisterRepository registerRepository;
    private final RegisterQueryRepository registerQueryRepository;

    /**
     * (등록)상품 조회
     */

    /**
     * v2: 일반 Join - 엔티티
     * : Lazy 로딩에 의한 DB 쿼리가 너무 많이 나감
     * => register 1번 - member, item N번(register 조회수만큼)
     * => 영속성 컨텍스트에 존재하지 않으면 계속 DB 에 쿼리가 나가므로
     * => 상당히 많은 쿼리가 나감 => 최적화 필요 => Fetch join(v3)
     */
    @GetMapping("/api/v2/registers")
    public Result<List<RegisterDto>> registersV2() {
        List<Register> registers = registerRepository.findAllByString(new RegisterSearch());

        List<RegisterDto> result = registers.stream()
                .map(r -> new RegisterDto(r))
                .collect(Collectors.toList());

        return new Result<>(result.size(), result);
    }

    /**
     * v3: Fetch Join - 엔티티
     *  v2랑 v3는 로직은 같지만 쿼리가 완전히 다름, Fetch join: 한방쿼리
     *  한 번에 다 끌고와서 엔티티 -> Dto 로 변환하는 단점이 있음 => 최적화: 직접 Dto 이용(v4)
     *  register 를 기준으로 X To One 은 fetch join 을 하고
     *  => ToOne 은 데이터 뻥튀기가 안되므로 한 번에 끌고와야 함
     *  => Member(다대일), Item(일대일) 이므로 한방쿼리로 나옴
     *  => default_batch_fetch_size: 100 # where 절에서 IN 쿼리의 개수 => id 개수
     *  => 1 + N => 1로 되어버림
     *  => 조인보다 DB 데이터 전송량이 최적화 됨
     */
    @GetMapping("/api/v3/registers")
    public Result<List<RegisterDto>> registersV3() {
        List<Register> registers = registerRepository.findAllWithMemberItem();

        List<RegisterDto> result = registers.stream()
                .map(r -> new RegisterDto(r))
                .collect(Collectors.toList());

        // Object 타입 {...}으로 반환, Result 라는 껍데기를 씌어서 data 필드의 값은 List 가 나가게됨
        // Object 타입으로 반환하지 않으면 배열타입 [...] 으로 나가게됨 => 확장성, 유연성 X
        return new Result<>(result.size(), result);
    }

    /**
     * v4: 일반 Join - Dto
     * : ToOne 관계(M, D)들 조회하고 => findRegisters()
     * tradeoff => Fetch 조인(V3)보다 쿼리를 직접 작성하는 양도 많지만,
     * 장점은 Fetch 조인보다 확실히 데이터를 select 한 양이 줄어듦
     */
    @GetMapping("/api/v4/registers")
    public Result<List<RegisterQueryDto>> registersV4() {
        List<RegisterQueryDto> allByDto = registerQueryRepository.findAllByDto();

        return new Result<>(allByDto.size(), allByDto);
    }

    /**
     * v3와 v4의 차이점
     * Fetch join(v3)는 hibernate.default_batch_fetch_size , @BatchSize 같이
     * 코드를 거의 수정하지 않고, 옵션만 약간 변경해서, 다양한 성능 최적화를 시도할 수 있다.
     * 반면에 DTO 를 직접 조회하는 방식(v4)은 성능을 최적화하거나 성능최적화 방식을 변경할 때 많은 코드를 변경해야 한다.
     * V3 (Fetch join)의 경우 join 은 V4와 성능이 같지만, Select 절에서 데이터를 많이 긁어오므로
     * DB 에서 많이 퍼올리게됨 => 네트워크 리소스 잡아먹게 됨
     * 반면 V4에서는 Select 절이 확 줄어듦 => 왜냐하면 직접 쿼리를 짰기때문에 필요한 것만 가져옴
     * 하지만 V3와 V4의 우열을 가릴 수 없음 => V3는 내가 원하는 테이블을 조인하여 값을 가져온 것이고
     * V4는 실제 SQL 짜듯이 필요한 것 전부 가져온 것 => 더이상 재사용성 X => 딱 이거일 때만 사용해야함
     * 하지만 V3는 재사용성이 높음 => Fetch join 으로 가져오고 그걸 다른 Dto 로 변환해서 사용이 가능함
     * V4는 성능 최적화지만 엔티티를 Dto 로 바꿔서 가져온 게 아니고 직접 Dto 로 조회한 것이므로 값 변경이 안되고
     * V3는 V4보다 성능은 떨어지지만 (차이는 미비함) 재사용성이 높고, 코드 상 V4는 지저분해지게 됨
     * 또한 API 스펙에 맞춘 코드가 repository 에 들어가게 됨 => repository 사용은 엔티티에 대한 객체 그래프 탐색이여야함
     * => repository 에서 Query 용 디렉토리를 따로 생성하여 관리 => RegisterQueryRepository
     */

    /**
     * 쿼리 방식 선택 권장 순서 (V3 <-> V4)
     1. 우선 엔티티를 DTO 로 변환하는 방법을 선택(V2) -> 코드 유지보수성 좋음
     2. 필요하면 Fetch join 으로 성능을 최적화한다(V3) -> 대부분의 성능이슈 해결가능, 90 %
     3. 그래도 안되면 DTO 로 직접 조회하는 방법을 사용(V4)
     4. 최후의 방법은 JPA 가 제공하는 네이티브 SQL 이나 SpringJDBCTemplate 을 사용해서 SQL 직접 사용
     */

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private int count;
        private T data;
    }
}
