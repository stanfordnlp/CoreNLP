for e in per org; do
    for t in residence birth death headquarters; do
        if [ -f "${e}:country_of_${t}.rules" ]; then
            echo "Copying ${e}:country_of_${t}.rules to state/province and city..."
            cat "${e}:country_of_${t}.rules" | sed -e "s/COUNTRY|NATIONALITY/STATE_OR_PROVINCE/g" > "${e}:stateorprovince_of_${t}.rules"
            cat "${e}:country_of_${t}.rules" | sed -e "s/COUNTRY|NATIONALITY/CITY/g" > "${e}:city_of_${t}.rules"
        elif [ -f "${e}:countries_of_${t}.rules" ]; then
            echo "Copying ${e}:countries_of_${t}.rules to states/provinces and cities..."
            cat "${e}:countries_of_${t}.rules" | sed -e "s/COUNTRY|NATIONALITY/STATE_OR_PROVINCE/g" > "${e}:stateorprovinces_of_${t}.rules"
            cat "${e}:countries_of_${t}.rules" | sed -e "s/COUNTRY|NATIONALITY/CITY/g" > "${e}:cities_of_${t}.rules"
        fi
    done
done
