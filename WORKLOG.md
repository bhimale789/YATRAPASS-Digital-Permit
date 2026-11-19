# YatraPass Backend: Work Log

**Developer:** Armik Gurung (Backend Intern)
**Organization:** IT Deurali Nepal Pvt. Ltd.
**Period:** 19 Aug 2026 – 6 Nov 2026

> **Note on commit history:** The code in this repository was committed and pushed after the internship work was done, so commit timestamps reflect the upload date, not the date the work happened. This log records when each piece of work was actually done, based on the approved intern attendance sheet.

**Attendance summary:** 60 working days; 56 present (93%), 4 absent (7%).

---

## Week 1: 19–21 Aug (Onboarding)
| Date | Hours | Work |
|---|---|---|
| Wed 19 Aug | 6.75 | Office orientation, met the team, learned work culture and office rules |
| Thu 20 Aug | 6.25 | Set up laptop with JDK 21, IntelliJ IDEA, Git and Postman |
| Fri 21 Aug | 5.75 | Learned Git basics: commit, push, pull, branching and pull requests |

## Week 2: 23–28 Aug (Java fundamentals)
| Date | Hours | Work |
|---|---|---|
| Sun 23 Aug | 6.75 | Revised Java basics: data types, loops, methods and arrays |
| Mon 24 Aug | 5.5 | Java OOP: classes, inheritance, polymorphism and interfaces |
| Tue 25 Aug | 6 | Weekly review with mentor; planned next week's learning |
| Wed 26 Aug | 6.25 | Java collections (List, Set, Map) and generics |
| Thu 27 Aug | 6 | Exception handling and Java date/time API |
| Fri 28 Aug | — | Holiday (Janai Purnima) |

## Week 3: 30 Aug – 4 Sep (Build tools and Spring Boot intro)
| Date | Hours | Work |
|---|---|---|
| Sun 30 Aug | 5.5 | Lambda expressions and streams |
| Mon 31 Aug | 6.25 | Maven/Gradle build tools and dependencies |
| Tue 1 Sep | 6.25 | Introduction to Spring Boot and dependency injection |
| Wed 2 Sep | 6.5 | (WFH) Sample Spring Boot project with Spring Initializr |
| Thu 3 Sep | 6.25 | Simple REST API with Spring Boot, tested in Postman |
| Fri 4 Sep | — | Holiday (Shree Krishna Janmashtami) |

## Week 4: 6–11 Sep (Persistence, Docker, project kickoff)
| Date | Hours | Work |
|---|---|---|
| Sun 6 Sep | 6.75 | (WFH) Controller/service/repository layers and DTOs |
| Mon 7 Sep | 7 | Installed PostgreSQL; revised SQL queries, joins and constraints |
| Tue 8 Sep | 5.25 | Spring Data JPA and Hibernate; connected API to PostgreSQL |
| Wed 9 Sep | 5.25 | Docker Desktop: images, containers and volumes |
| Thu 10 Sep | 5.75 | Docker Compose for app + database; weekly review |
| Fri 11 Sep | 7 | Introduction to the YatraPass project, its users and goals |

## Week 5: 13–18 Sep (Design and project setup)
| Date | Hours | Work |
|---|---|---|
| Sun 13 Sep | 5.5 | Requirement analysis; use case diagram |
| Mon 14 Sep | 5.75 | Database table design and ER diagram |
| Tue 15 Sep | 6 | Set up YatraPass Spring Boot project and package structure |
| Wed 16 Sep | 6.25 | (WFH) Flyway; first database migrations |
| Thu 17 Sep | 6.5 | User roles (Tourist, Admin, Officer); seeded staff accounts |
| Fri 18 Sep | 5.75 | Spring Security basics and BCrypt password hashing |

## Week 6: 20–25 Sep (Auth and core APIs)
| Date | Hours | Work |
|---|---|---|
| Sun 20 Sep | 5.5 | Register and login APIs with JWT |
| Mon 21 Sep | 7 | Refresh token, logout and login rate limiting |
| Tue 22 Sep | 5.75 | Role-based access control on all APIs |
| Wed 23 Sep | 6 | Tourist profile APIs with input validation |
| Thu 24 Sep | 6 | Permit area APIs with pagination; weekly review |
| Fri 25 Sep | 5.5 | (WFH) Permit application APIs with date and overlap checks |

## Week 7: 27 Sep – 2 Oct (Application workflow)
| Date | Hours | Work |
|---|---|---|
| Sun 27 Sep | 6 | Application status flow (pending, review, approved, rejected) |
| Mon 28 Sep | 6.5 | Document upload with file type and size validation |
| Tue 29 Sep | 6 | (WFH) Admin APIs to review applications and verify documents |
| Wed 30 Sep | 6.5 | Approve/reject workflow that creates the permit |
| Thu 1 Oct | 6 | Daily job to expire permits after end date |
| Fri 2 Oct | 5.75 | Secure token generation, SHA-256 hashing and AES encryption |

## Week 8: 4–9 Oct (QR verification, tests, mid-term)
| Date | Hours | Work |
|---|---|---|
| Sun 4 Oct | 6.25 | Permit QR codes with ZXing |
| Mon 5 Oct | 6.25 | QR verification API with step-by-step permit checks |
| Tue 6 Oct | 6.75 | Verification and audit logs; permit PDF generation |
| Wed 7 Oct | 6.5 | Unit and integration tests (JUnit, Mockito, Testcontainers) |
| Thu 8 Oct | 5.75 | Bug fixes, API documentation, mid-term report |
| Fri 9 Oct | 6.5 | (WFH) Driving routes and road-condition management |

## Week 9: 11–16 Oct (Dashboards)
| Date | Hours | Work |
|---|---|---|
| Sun 11 Oct | — | Holiday (Dashain) |
| Mon 12 Oct | — | Absent |
| Tue 13 Oct | 6.25 | Admin and officer dashboards |
| Wed 14 Oct | — | Absent |
| Thu 15 Oct | 6.5 | Completed driving routes, road-condition management and dashboards |
| Fri 16 Oct | — | Absent |

## Week 10: 17–24 Oct
Holiday (Dashain).

## Week 11: 25–30 Oct (Deployment and feedback)
| Date | Hours | Work |
|---|---|---|
| Sun 25 Oct | 7 | Docker deployment and configuration of the full system |
| Mon 26 Oct | 6.25 | Backend improvements from team and supervisor feedback |
| Tue 27 Oct | — | Absent |
| Wed 28 Oct | 6.5 | (WFH) Fixed issues found during testing |
| Thu 29 Oct | 6.5 | Email/SMS notifications for approved, rejected or expiring applications |
| Fri 30 Oct | 5.75 | Researched payment gateways (eSewa, Khalti) |

## Week 12: 1–6 Nov (Hardening, documentation, final report)
| Date | Hours | Work |
|---|---|---|
| Sun 1 Nov | 6.5 | Explored staff account management and admin 2FA |
| Mon 2 Nov | 6.25 | Production prep: shared rate limiting and object storage |
| Tue 3 Nov | 6.5 | Technical documentation |
| Wed 4 Nov | 5.75 | Technical documentation (continued) |
| Thu 5 Nov | 6.75 | Final internship report |
| Fri 6 Nov | 6 | Final review of report and project |

*8 Nov: Holiday (Tihar).*
