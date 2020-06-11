<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Corda on boarding project

On boarding project using Corda Token SDK and Account SDK.

From LocalBank node shell:
 >    start IssueFiatCurrencyFlow currency: USD, amount: 100, recipient: "O=Company,L=Tokyo,C=JP"
 
 From Company node shell:
 >    run vaultQuery contractStateType: net.corda.core.contracts.FungibleState
 
 From LocalBank node shell;
 >    start CreateAndIssueLocalCoinFlow name: ETH, currency: USD, price: 1, volume: 100, holder: 

 From