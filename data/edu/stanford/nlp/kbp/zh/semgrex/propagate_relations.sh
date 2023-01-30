RELATION='per:cities_of_residence' TARGET='per:countries_of_residence' SRC_NER='CITY' TARGET_NER='COUNTRY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules
RELATION='per:cities_of_residence' TARGET='per:countries_of_residence' SRC_NER='CITY' TARGET_NER='NATIONALITY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" >> $TARGET.rules
RELATION='per:cities_of_residence' TARGET='per:stateorprovinces_of_residence' SRC_NER='CITY' TARGET_NER='STATE_OR_PROVINCE'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules

RELATION='per:city_of_birth' TARGET='per:country_of_birth' SRC_NER='CITY' TARGET_NER='COUNTRY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules
RELATION='per:city_of_birth' TARGET='per:country_of_birth' SRC_NER='CITY' TARGET_NER='NATIONALITY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" >> $TARGET.rules
RELATION='per:city_of_birth' TARGET='per:stateorprovince_of_birth' SRC_NER='CITY' TARGET_NER='STATE_OR_PROVINCE'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules

RELATION='per:city_of_death' TARGET='per:country_of_death' SRC_NER='CITY' TARGET_NER='COUNTRY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules
RELATION='per:city_of_death' TARGET='per:country_of_death' SRC_NER='CITY' TARGET_NER='NATIONALITY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" >> $TARGET.rules
RELATION='per:city_of_death' TARGET='per:stateorprovince_of_death' SRC_NER='CITY' TARGET_NER='STATE_OR_PROVINCE'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules

RELATION='org:city_of_headquarters' TARGET='org:country_of_headquarters' SRC_NER='CITY' TARGET_NER='COUNTRY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules
RELATION='org:city_of_headquarters' TARGET='org:country_of_headquarters' SRC_NER='CITY' TARGET_NER='NATIONALITY'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" >> $TARGET.rules
RELATION='org:city_of_headquarters' TARGET='org:stateorprovince_of_headquarters' SRC_NER='CITY' TARGET_NER='STATE_OR_PROVINCE'
cat $RELATION.rules | sed -e "s/$SRC_NER/$TARGET_NER/g" > $TARGET.rules
